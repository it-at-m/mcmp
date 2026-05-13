package de.muenchen.mcmp.clients.greenit;

import de.muenchen.mcmp.appservice.Appservice;
import de.muenchen.mcmp.appservice.AppserviceService;
import de.muenchen.mcmp.clients.greenit.vmware.rightsizing.*;
import de.muenchen.mcmp.clients.greenit.vmware.shutdown.VMwareShutdownMailDataItemDTO;
import de.muenchen.mcmp.clients.greenit.vmware.shutdown.VMwareShutdownMailResponseDTO;
import de.muenchen.mcmp.clients.greenit.vmware.shutdown.VMwareShutdownRequestDTO;
import de.muenchen.mcmp.exception.AppServiceNotFoundException;
import de.muenchen.mcmp.exception.ExcelGenerationException;
import de.muenchen.mcmp.exception.GreenITServerLockedException;
import de.muenchen.mcmp.exception.ServerNotFoundException;
import de.muenchen.mcmp.greenit.metrics.ServerMetricsService;
import de.muenchen.mcmp.greenit.shutdown.GreenItShutdown;
import de.muenchen.mcmp.greenit.shutdown.GreenItShutdownService;
import de.muenchen.mcmp.greenit.rightsizing.GreenItRightsizing;
import de.muenchen.mcmp.greenit.rightsizing.GreenItRightsizingService;
import de.muenchen.mcmp.job.Job;
import de.muenchen.mcmp.job.JobService;
import de.muenchen.mcmp.mail.MailDTO;
import de.muenchen.mcmp.mail.MailService;
import de.muenchen.mcmp.server.Server;
import de.muenchen.mcmp.server.ServerService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Service for handling GreenIT VMware optimization requests (rightsizing and shutdown).
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Persist incoming optimization requests ({@link GreenItRightsizing}, {@link GreenItShutdown}).</li>
 *   <li>Resolve the {@link Appservice} for the affected {@link Server} (single assigned appservice, otherwise fallback to configured default).</li>
 *   <li>Enforce a lockout window after rejected changes and prevent new requests while a change is pending.</li>
 *   <li>Create corresponding MCMP jobs via {@link JobService}.</li>
 *   <li>Generate Excel reports and send them via {@link MailService}.</li>
 * </ul>
 * <p>
 * Time zone and formatting for mail subjects/attachments are based on {@code Europe/Berlin}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GreenITService {

    public static final String EXCEL_MIME_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    public static final String EXCEL_FILE_SUFFIX = ".xlsx";
    private static final ZoneId BERLIN_ZONE = ZoneId.of("Europe/Berlin");
    private static final DateTimeFormatter FILENAME_CURRENT_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final DateTimeFormatter FILENAME_START_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter SUBJECT_CURRENT_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
    private static final DateTimeFormatter SUBJECT_START_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final int MB_PER_GB = 1024;
    private final ServerService serverService;
    private final ServerMetricsService serverMetricsService;
    private final JobService jobService;
    private final AppserviceService appserviceService;
    private final GreenItRightsizingService greenItRightsizingService;
    private final GreenItShutdownService greenItShutdownService;
    private final MailService mailService;
    @Value("${greenit.lockout-period-months:6}")
    private Integer lockoutPeriodMonths;

    @Value("${greenit.default-appservice-number}")
    private String defaultAppserviceNumber;

    @Value("${greenit.mail-to}")
    private String mailTo;

    @Value("${greenit.subject-rightsize}")
    private String rightsizingMailSubject;

    @Value("${greenit.subject-power-off}")
    private String shutdownMailSubject;

    @Value("${greenit.filename-rightsize}")
    private String rightsizingFilenamePrefix;

    @Value("${greenit.filename-power-off}")
    private String shutdownFilenamePrefix;

    /**
     * Processes a VMware rightsizing optimization request coming from GreenIT.
     *
     * <p>This method:
     * <ul>
     *   <li>Resolves the target {@code Server} by vCenter short code and server UUID.</li>
     *   <li>Builds and persists a {@code GreenItRightsizing} entity with current and requested sizing values.</li>
     *   <li>Resolves the owning {@code Appservice} (single assignment preferred, otherwise default fallback).</li>
     *   <li>Applies lockout rules (pending change / recent rejection window) and rejects requests accordingly.</li>
     *   <li>Creates the corresponding MCMP job and returns a response containing the job identifier.</li>
     * </ul>
     *
     * <p><b>Side effects:</b> Persists data, may update request status, may create a job.
     *
     * @param request the rightsizing request payload (server identity, start time, and new CPU/RAM targets)
     * @return a response containing the created job id and a human-readable message
     * @throws de.muenchen.mcmp.exception.ServerNotFoundException      if the server cannot be resolved
     * @throws de.muenchen.mcmp.exception.AppServiceNotFoundException  if no appservice can be resolved (including default)
     * @throws de.muenchen.mcmp.exception.GreenITServerLockedException if the server is locked due to pending/recently rejected changes
     */
    public GreenITResponseDTO processVmwareRightsizing(final VMwareRightsizeRequestDTO request) {
        final Server server = findServerOrThrow(request.vcenterShortCode(), request.serverUuid());

        final GreenItRightsizing rightsizing = GreenItRightsizing.builder()
                .vmName(server.getName())
                .startTime(request.startTime())
                .cpuCurrent(server.getNumCpu())
                .cpuNew(request.cpu())
                .ramCurrent(server.getMemoryMb())
                .ramNew(request.ram() * MB_PER_GB)
                .serverUuid(server.getUuid())
                .vcenterShortCode(request.vcenterShortCode())
                .server(server)
                .build();

        return processGreenItOperation(
                rightsizing,
                server,
                greenItRightsizingService::save,
                GreenItRightsizing::setStatus,
                GreenItRightsizing::setAppservice,
                server.getGreenItRightsizingChangeRejectedDate(),
                server.getGreenItRightsizingChangePending(),
                "rightsizing",
                jobService::createGreenItRightsizingJob
        );
    }

    /**
     * Processes a VMware shutdown optimization request coming from GreenIT.
     *
     * <p>This method:
     * <ul>
     *   <li>Resolves the target {@code Server} by vCenter short code and server UUID.</li>
     *   <li>Builds and persists a {@code GreenItShutdown} entity representing the requested shutdown.</li>
     *   <li>Resolves the owning {@code Appservice} (single assignment preferred, otherwise default fallback).</li>
     *   <li>Applies lockout rules (pending change / recent rejection window) and rejects requests accordingly.</li>
     *   <li>Creates the corresponding MCMP job and returns a response containing the job identifier.</li>
     * </ul>
     *
     * <p><b>Side effects:</b> Persists data, may update request status, may create a job.
     *
     * @param request the shutdown request payload (server identity and start time)
     * @return a response containing the created job id and a human-readable message
     * @throws de.muenchen.mcmp.exception.ServerNotFoundException      if the server cannot be resolved
     * @throws de.muenchen.mcmp.exception.AppServiceNotFoundException  if no appservice can be resolved (including default)
     * @throws de.muenchen.mcmp.exception.GreenITServerLockedException if the server is locked due to pending/recently rejected changes
     */
    public GreenITResponseDTO processVmwareShutdown(final VMwareShutdownRequestDTO request) {
        final Server server = findServerOrThrow(request.vcenterShortCode(), request.serverUuid());

        final GreenItShutdown shutdown = GreenItShutdown.builder()
                .vmName(server.getName())
                .cpuCurrent(server.getNumCpu())
                .ramCurrent(server.getMemoryMb())
                .startTime(request.startTime())
                .serverUuid(server.getUuid())
                .vcenterShortCode(request.vcenterShortCode())
                .server(server)
                .build();

        return processGreenItOperation(
                shutdown,
                server,
                greenItShutdownService::save,
                GreenItShutdown::setStatus,
                GreenItShutdown::setAppservice,
                server.getGreenItShutdownChangeRejectedDate(),
                server.getGreenItShutdownChangePending(),
                "shutdown",
                jobService::createGreenItShutdownJob
        );
    }

    /**
     * Generates and sends the VMware rightsizing report email for a given start time.
     *
     * <p>This method:
     * <ul>
     *   <li>Loads all rightsizing requests matching the given start date/time.</li>
     *   <li>Builds the JSON payload items returned to the caller (mail preview / API response).</li>
     *   <li>Generates an Excel report attachment (XLSX) including operational and job-related columns.</li>
     *   <li>Sends the email with the generated attachment to the configured recipient(s).</li>
     * </ul>
     *
     * <p><b>Excel generation:</b> Uses Apache POI to build an {@code .xlsx} file in-memory.
     * <br><b>Time zone:</b> Subject and filenames are formatted using {@code Europe/Berlin}.
     *
     * @param sendMailRequestDTO request containing the reporting start time used to select records and format the email
     * @return response containing a success message and the list of mail data items
     * @throws de.muenchen.mcmp.exception.ExcelGenerationException if the Excel workbook cannot be generated
     */
    public VMwareRightsizeMailResponseDTO sendVmwareRightsizingMail(final SendMailRequestDTO sendMailRequestDTO) {
        final List<GreenItRightsizing> rightsizingRequests = greenItRightsizingService.findByStartDate(sendMailRequestDTO.startTime());

        // JSON payload
        final List<VMwareRightsizeMailDataItemDTO> mailItems = mapToRightsizingMailItems(rightsizingRequests);

        // Create Excel workbook
        final String[] excelColumnHeaders = {"VM Name", "Start Time", "Current CPU", "New CPU", "Current RAM", "New RAM", "Service", "Change", "MCMP Job ID", "MCMP Job Status", "Status", "Change Link"};


        byte[] excelBytes;
        try {
            excelBytes = generateExcelReport("VMware Rightsizing", excelColumnHeaders, rightsizingRequests, (row, rightsizing, linkStyle) -> {
                int columnIndex = 0;
                row.createCell(columnIndex++).setCellValue(rightsizing.getVmName());
                row.createCell(columnIndex++).setCellValue(rightsizing.getStartTime().toString());
                row.createCell(columnIndex++, CellType.NUMERIC).setCellValue(rightsizing.getCpuCurrent());
                row.createCell(columnIndex++, CellType.NUMERIC).setCellValue(rightsizing.getCpuNew());
                row.createCell(columnIndex++, CellType.NUMERIC).setCellValue(rightsizing.getRamCurrent());
                row.createCell(columnIndex++, CellType.NUMERIC).setCellValue(rightsizing.getRamNew());
                row.createCell(columnIndex++).setCellValue(getAppserviceName(rightsizing.getAppservice()));
                row.createCell(columnIndex++).setCellValue(getChangeNumber(rightsizing.getJob()));

                writeJobIdCell(row, columnIndex++, rightsizing.getJob());
                row.createCell(columnIndex++).setCellValue(getJobStatus(rightsizing.getJob()));
                row.createCell(columnIndex++).setCellValue(getStatus(rightsizing.getStatus()));

                writeHyperlinkCell(row, columnIndex++, getChangeLink(rightsizing.getJob()), linkStyle);
            });
        } catch (IOException e) {
            throw new ExcelGenerationException("Failed to generate rightsizing Excel report", e);
        }
        sendReportEmail(sendMailRequestDTO.startTime(), excelBytes, rightsizingMailSubject, rightsizingFilenamePrefix);
        return new VMwareRightsizeMailResponseDTO("Email sent successfully.", mailItems);
    }

    /**
     * Generates and sends the VMware shutdown report email for a given start time.
     *
     * <p>This method:
     * <ul>
     *   <li>Loads all shutdown requests matching the given start date/time.</li>
     *   <li>Builds the JSON payload items returned to the caller (mail preview / API response).</li>
     *   <li>Generates an Excel report attachment (XLSX) including operational and job-related columns.</li>
     *   <li>Sends the email with the generated attachment to the configured recipient(s).</li>
     * </ul>
     *
     * <p><b>Excel generation:</b> Uses Apache POI to build an {@code .xlsx} file in-memory.
     * <br><b>Time zone:</b> Subject and filenames are formatted using {@code Europe/Berlin}.
     *
     * @param sendMailRequestDTO request containing the reporting start time used to select records and format the email
     * @return response containing a success message and the list of mail data items
     * @throws de.muenchen.mcmp.exception.ExcelGenerationException if the Excel workbook cannot be generated
     */
    public VMwareShutdownMailResponseDTO sendVmwareShutdownMail(final SendMailRequestDTO sendMailRequestDTO) {
        final List<GreenItShutdown> shutdownRequests = greenItShutdownService.findByStartDate(sendMailRequestDTO.startTime());

        // JSON payload
        final List<VMwareShutdownMailDataItemDTO> mailDataItems = mapToShutdownMailItems(shutdownRequests);

        // Create Excel workbook
        final String[] excelColumnHeaders = {"VM Name", "Start Time", "Current CPU", "Current RAM", "Service", "Change", "MCMP Job ID", "MCMP Job Status", "Status", "Change Link"};


        byte[] excelBytes;
        try {
            excelBytes = generateExcelReport("VMware Shutdown", excelColumnHeaders, shutdownRequests, (row, shutdown, linkStyle) -> {
                int columnIndex = 0;
                row.createCell(columnIndex++).setCellValue(shutdown.getVmName());
                row.createCell(columnIndex++).setCellValue(shutdown.getStartTime().toString());
                row.createCell(columnIndex++, CellType.NUMERIC).setCellValue(shutdown.getCpuCurrent());
                row.createCell(columnIndex++, CellType.NUMERIC).setCellValue(shutdown.getRamCurrent());
                row.createCell(columnIndex++).setCellValue(getAppserviceName(shutdown.getAppservice()));
                row.createCell(columnIndex++).setCellValue(getChangeNumber(shutdown.getJob()));

                writeJobIdCell(row, columnIndex++, shutdown.getJob());
                row.createCell(columnIndex++).setCellValue(getJobStatus(shutdown.getJob()));
                row.createCell(columnIndex++).setCellValue(getStatus(shutdown.getStatus()));

                writeHyperlinkCell(row, columnIndex++, getChangeLink(shutdown.getJob()), linkStyle);
            });
        } catch (IOException e) {
            throw new ExcelGenerationException("Failed to generate shutdown Excel report", e);
        }
        sendReportEmail(sendMailRequestDTO.startTime(), excelBytes, shutdownMailSubject, shutdownFilenamePrefix);
        return new VMwareShutdownMailResponseDTO("Email sent successfully.", mailDataItems);
    }

    /**
     * Maps persisted rightsizing entities into mail/API DTO items.
     *
     * <p>The mapping is intended for external consumption and therefore:
     * <ul>
     *   <li>Flattens entity relationships (e.g., appservice name, change number).</li>
     *   <li>Converts date/time values to string form.</li>
     *   <li>Normalizes nullable values to empty strings where applicable.</li>
     * </ul>
     *
     * @param greenItRightsizingList list of rightsizing entities to convert
     * @return list of DTO items suitable for JSON serialization; never {@code null}
     */
    private @NonNull List<VMwareRightsizeMailDataItemDTO> mapToRightsizingMailItems(final List<GreenItRightsizing> greenItRightsizingList) {
        return greenItRightsizingList.stream()
                .map(rightsizing -> new VMwareRightsizeMailDataItemDTO(
                        rightsizing.getVmName(),
                        rightsizing.getStartTime().toString(),
                        rightsizing.getCpuCurrent(),
                        rightsizing.getCpuNew(),
                        rightsizing.getRamCurrent(),
                        rightsizing.getRamNew(),
                        getAppserviceName(rightsizing.getAppservice()),
                        getChangeNumber(rightsizing.getJob()),
                        getStatus(rightsizing.getStatus())
                ))
                .toList();
    }

    /**
     * Maps persisted shutdown entities into mail/API DTO items.
     *
     * <p>The mapping is intended for external consumption and therefore:
     * <ul>
     *   <li>Flattens entity relationships (e.g., appservice name, change number).</li>
     *   <li>Converts date/time values to string form.</li>
     *   <li>Normalizes nullable values to empty strings where applicable.</li>
     * </ul>
     *
     * @param greenItShutdownList list of shutdown entities to convert
     * @return list of DTO items suitable for JSON serialization; never {@code null}
     */
    private @NonNull List<VMwareShutdownMailDataItemDTO> mapToShutdownMailItems(final List<GreenItShutdown> greenItShutdownList) {
        return greenItShutdownList.stream()
                .map(shutdown -> new VMwareShutdownMailDataItemDTO(
                        shutdown.getVmName(),
                        shutdown.getStartTime().toString(),
                        shutdown.getCpuCurrent(),
                        shutdown.getRamCurrent(),
                        getAppserviceName(shutdown.getAppservice()),
                        getChangeNumber(shutdown.getJob()),
                        getStatus(shutdown.getStatus())
                ))
                .toList();
    }

    /**
     * Creates an in-memory Excel report (XLSX) for the given data set.
     *
     * <p>This helper:
     * <ul>
     *   <li>Creates a workbook and a single sheet with the provided name.</li>
     *   <li>Writes a header row using bold styling.</li>
     *   <li>Iterates the data list and delegates row population to {@code populator}.</li>
     *   <li>Applies autosizing for all header columns.</li>
     * </ul>
     *
     * <p><b>Resource management:</b> The workbook is closed automatically; the returned byte array
     * represents the full XLSX file content.
     *
     * @param sheetName the worksheet name to create
     * @param headers   column headers written as the first row
     * @param data      data items to be written starting from row index 1
     * @param populator callback responsible for populating each row for a given item (including hyperlink styling)
     * @param <T>       the element type of the data list
     * @return XLSX bytes ready to be attached to an email or persisted
     * @throws java.io.IOException if writing the workbook fails
     */
    private <T> byte[] generateExcelReport(String sheetName, String[] headers, List<T> data, ExcelRowPopulator<T> populator) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            final Sheet sheet = workbook.createSheet(sheetName);

            final CellStyle headerStyle = workbook.createCellStyle();
            final Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            final CellStyle linkStyle = workbook.createCellStyle();
            final Font linkFont = workbook.createFont();
            linkFont.setUnderline(Font.U_SINGLE);
            linkFont.setColor(IndexedColors.BLUE.getIndex());
            linkStyle.setFont(linkFont);

            final Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                final Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIndex = 1;
            for (final T item : data) {
                final Row row = sheet.createRow(rowIndex++);
                populator.populate(row, item, linkStyle);
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    /**
     * Sends a report email with an XLSX attachment for a given start time.
     *
     * <p>The subject and attachment filename are assembled from:
     * <ul>
     *   <li>a configured prefix (subject/filename),</li>
     *   <li>the report start date,</li>
     *   <li>the current generation timestamp,</li>
     *   <li>the fixed XLSX file suffix and MIME type.</li>
     * </ul>
     *
     * <p><b>Time zone:</b> All timestamps used for subject/filename generation are based on {@code Europe/Berlin}.
     *
     * @param startTime      logical report start time used for subject and filename formatting
     * @param attachmentData the raw XLSX bytes to attach
     * @param subjectPrefix  subject prefix configured per report type
     * @param filenamePrefix filename prefix configured per report type
     */
    private void sendReportEmail(TemporalAccessor startTime, byte[] attachmentData, String subjectPrefix, String filenamePrefix) {
        final ZonedDateTime nowBerlin = ZonedDateTime.now(BERLIN_ZONE);
        final String generatedAtForFilename = nowBerlin.format(FILENAME_CURRENT_DATE_FORMATTER);
        final String startDateForFilename = FILENAME_START_DATE_FORMATTER.format(startTime);
        final String generatedAtForSubject = nowBerlin.format(SUBJECT_CURRENT_DATE_FORMATTER);
        final String startDateForSubject = SUBJECT_START_DATE_FORMATTER.format(startTime);

        final List<String> recipients = Arrays.stream(Objects.requireNonNullElse(mailTo, "").split("[,;]"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();

        final MailDTO mail = MailDTO.builder()
                .to(recipients)
                .content("")
                .isHtml(false)
                .subject(subjectPrefix + " " + startDateForSubject + " (" + generatedAtForSubject + ")")
                .attachment(MailDTO.Attachment.builder()
                        .filename(filenamePrefix + "_" + startDateForFilename + "___" + generatedAtForFilename + EXCEL_FILE_SUFFIX)
                        .data(attachmentData)
                        .mimeType(EXCEL_MIME_TYPE)
                        .build())
                .build();
        mailService.sendEmail(mail);
    }

    /**
     * Writes the MCMP job id into a numeric cell if a job is present.
     *
     * <p>If {@code job} is {@code null}, this method does nothing and leaves the cell unset.</p>
     *
     * @param row       target Excel row
     * @param cellIndex zero-based cell index in the row
     * @param job       job from which the id is taken; may be {@code null}
     */
    private void writeJobIdCell(Row row, int cellIndex, Job job) {
        if (job != null) {
            row.createCell(cellIndex, CellType.NUMERIC).setCellValue(job.getId());
        }
    }

    /**
     * Writes a hyperlink cell pointing to the given change link.
     *
     * <p>This method always writes the cell value (maybe empty). If {@code changeLink} is non-empty,
     * it additionally creates an Excel hyperlink of type {@code URL} and applies the provided link style.</p>
     *
     * @param row        target Excel row
     * @param cellIndex  zero-based cell index in the row
     * @param changeLink URL to link to; may be {@code null} or empty
     * @param linkStyle  style to apply when a hyperlink is created
     */
    private void writeHyperlinkCell(Row row, int cellIndex, String changeLink, CellStyle linkStyle) {
        final Cell linkCell = row.createCell(cellIndex);
        linkCell.setCellValue(changeLink);

        if (changeLink != null && !changeLink.isEmpty()) {
            final CreationHelper createHelper = row.getSheet().getWorkbook().getCreationHelper();
            final Hyperlink link = createHelper.createHyperlink(HyperlinkType.URL);
            link.setAddress(changeLink);
            linkCell.setHyperlink(link);
            linkCell.setCellStyle(linkStyle);
        }
    }

    /**
     * Returns the appservice name or an empty string if no appservice is present.
     *
     * @param appService appservice instance; may be {@code null}
     * @return appservice name, or {@code ""} if {@code appService} is {@code null}
     */
    private String getAppserviceName(Appservice appService) {
        return appService != null ? appService.getName() : "";
    }

    /**
     * Returns the change number associated with the given job or an empty string if not available.
     *
     * @param job job instance; may be {@code null}
     * @return change number, or {@code ""} if {@code job} is {@code null}
     */
    private String getChangeNumber(Job job) {
        return job != null ? job.getChangeNumber() : "";
    }

    /**
     * Returns the job status name or an empty string if not available.
     *
     * @param job job instance; may be {@code null}
     * @return status enum name, or {@code ""} if {@code job} is {@code null}
     */
    private String getJobStatus(Job job) {
        return job != null ? job.getStatus().name() : "";
    }

    /**
     * Normalizes a nullable status string to a non-null value.
     *
     * @param status status string; may be {@code null}
     * @return {@code status} if non-null, otherwise {@code ""}
     */
    private String getStatus(String status) {
        return Objects.requireNonNullElse(status, "");
    }

    /**
     * Returns the change link associated with the given job or an empty string if not available.
     *
     * @param job job instance; may be {@code null}
     * @return change link URL, or {@code ""} if {@code job} is {@code null}
     */
    private String getChangeLink(Job job) {
        return job != null ? job.getChangeLink() : "";
    }

    /**
     * Processes a GreenIT operation using a shared template flow.
     *
     * <p>This method encapsulates the common lifecycle for different GreenIT operations (e.g. rightsizing, shutdown):
     * <ol>
     *   <li>Persist the initial entity.</li>
     *   <li>Resolve and set the {@code Appservice} (or record a failure status).</li>
     *   <li>Enforce lockout rules and pending-change checks (or record a failure status).</li>
     *   <li>Create an MCMP job (or record a failure status).</li>
     * </ol>
     *
     * <p><b>Error handling contract:</b> Any exception encountered during appservice resolution, locking checks,
     * or job creation is persisted into the entity status (via {@code setStatusFn}) before the exception is rethrown.
     *
     * @param entity          the operation entity being processed (rightsizing/shutdown)
     * @param server          the affected server
     * @param saveFn          persistence callback for the entity
     * @param setStatusFn     callback to record a human-readable status/error on the entity
     * @param setAppServiceFn callback to associate the resolved appservice with the entity
     * @param rejectedDate    last rejected date relevant for lockout evaluation; may be {@code null}
     * @param isChangePending whether a change is currently pending; may be {@code null}
     * @param operationType   human-readable operation type used in error messages (e.g. "rightsizing", "shutdown")
     * @param createJobFn     callback that creates the job and returns the new job id
     * @param <T>             entity type
     * @return response describing the created job
     * @throws RuntimeException rethrows any exception thrown by collaborators after persisting status
     */
    private <T> GreenITResponseDTO processGreenItOperation(
            T entity,
            Server server,
            java.util.function.Consumer<T> saveFn,
            java.util.function.BiConsumer<T, String> setStatusFn,
            java.util.function.BiConsumer<T, Appservice> setAppServiceFn,
            OffsetDateTime rejectedDate,
            Boolean isChangePending,
            String operationType,
            java.util.function.Function<T, Long> createJobFn
    ) {
        saveFn.accept(entity);

        final Appservice appService;
        try {
            appService = resolveApplicationService(server);
        } catch (AppServiceNotFoundException ex) {
            setStatusFn.accept(entity, ex.getMessage());
            saveFn.accept(entity);
            throw ex;
        }
        setAppServiceFn.accept(entity, appService);
        saveFn.accept(entity);

        try {
            assertServerNotLocked(rejectedDate, isChangePending, server.getName(), operationType);
        } catch (GreenITServerLockedException ex) {
            setStatusFn.accept(entity, ex.getMessage());
            saveFn.accept(entity);
            throw ex;
        }

        try {
            final Long jobId = createJobFn.apply(entity);
            return buildJobCreatedResponse(jobId, server.getName());
        } catch (Exception ex) {
            setStatusFn.accept(entity, ex.getMessage());
            saveFn.accept(entity);
            throw ex;
        }
    }

    /**
     * Resolves a server by vCenter short code and UUID, or throws if not found.
     *
     * <p>The error message includes both identifiers to support troubleshooting and client-side correction.</p>
     *
     * @param vcenterShortCode vCenter short code used for partitioning/lookup
     * @param serverUuid       server UUID used for unique identification
     * @return the resolved {@code Server}
     * @throws de.muenchen.mcmp.exception.ServerNotFoundException if no server matches the given identifiers
     */
    private Server findServerOrThrow(final String vcenterShortCode, final String serverUuid) {
        return serverService.findServerByVcenterShortCodeAndUuidOptional(vcenterShortCode, serverUuid)
                .orElseThrow(() -> {
                    final String errorMessage = "Server not found for vCenter short code '%s' and UUID '%s'.".formatted(vcenterShortCode, serverUuid);
                    return new ServerNotFoundException(errorMessage);
                });
    }

    /**
     * Resolves the application service (appservice) for a server.
     *
     * <p>Resolution strategy:
     * <ul>
     *   <li>If exactly one appservice is assigned to the server, use that one.</li>
     *   <li>Otherwise, fall back to the configured default appservice number.</li>
     * </ul>
     *
     * @param server server for which the appservice is required
     * @return a non-null appservice
     * @throws de.muenchen.mcmp.exception.AppServiceNotFoundException if no appservice can be found/resolved
     */
    private Appservice resolveApplicationService(final Server server) {
        // Use server's appservice only if exactly one is assigned; otherwise fall back to default
        final Appservice appService = server.getAppservices().size() == 1
                ? server.getAppservices().iterator().next()
                : appserviceService.findByNumber(defaultAppserviceNumber);

        if (appService == null) {
            throw new AppServiceNotFoundException("No application service found for server '%s'.".formatted(server.getName()));
        }
        return appService;
    }

    /**
     * Enforces GreenIT lockout rules for a server and operation type.
     *
     * <p>This method checks:
     * <ul>
     *   <li><b>Pending change:</b> if a change is already pending, the server is considered locked.</li>
     *   <li><b>Rejection lockout:</b> if the last rejection occurred within the configured lockout window,
     *       new operations are blocked until the lock expires.</li>
     * </ul>
     *
     * @param rejectedDate    timestamp of the last rejected change; may be {@code null} (meaning no lockout applies)
     * @param isChangePending whether there is an ongoing/pending change; may be {@code null}
     * @param serverName      server name used for user-facing error messages
     * @param operationType   operation label used for user-facing error messages
     * @throws de.muenchen.mcmp.exception.GreenITServerLockedException if the server is locked for this operation
     */
    private void assertServerNotLocked(final OffsetDateTime rejectedDate, final Boolean isChangePending, final String serverName, final String operationType) {
        if (isChangePending != null && isChangePending) {
            final String errorMessage = "Server '%s' is locked for GreenIT %s optimization because a change is already pending.".formatted(serverName, operationType);
            throw new GreenITServerLockedException(errorMessage);
        }
        if (rejectedDate == null) {
            return;
        }
        final OffsetDateTime cutoffDate = ZonedDateTime.now(BERLIN_ZONE)
                .minusMonths(lockoutPeriodMonths)
                .toOffsetDateTime();

        if (rejectedDate.isAfter(cutoffDate)) {
            final OffsetDateTime lockExpiry = rejectedDate.plusMonths(lockoutPeriodMonths);
            final String errorMessage = "Server '%s' is locked for GreenIT %s optimization because the last rejected change (%s) blocks new operations until %s.".formatted(serverName, operationType, rejectedDate, lockExpiry);
            throw new GreenITServerLockedException(errorMessage);
        }
    }

    /**
     * Builds a standardized response for a successfully created job.
     *
     * @param jobId      identifier of the created job
     * @param serverName name of the server the job targets
     * @return a response containing the job id and a descriptive message
     */
    private GreenITResponseDTO buildJobCreatedResponse(final Long jobId, final String serverName) {
        final String message = "Created job %d for server '%s'.".formatted(jobId, serverName);
        return new GreenITResponseDTO(jobId, message);
    }

    /**
     * Populates a single Excel row for a given data item.
     *
     * <p>Implementations are expected to create cells in a consistent order matching the report headers
     * and to apply {@code linkStyle} for hyperlink cells where appropriate.</p>
     *
     * @param <T> data item type
     */
    @FunctionalInterface
    private interface ExcelRowPopulator<T> {

        /**
         * Populates the given {@code row} with values from {@code item}, using {@code linkStyle} for hyperlinks.
         *
         * @param row       the Excel row to populate
         * @param item      the source data item for the row
         * @param linkStyle style to apply to hyperlink cells
         */
        void populate(Row row, T item, CellStyle linkStyle);
    }

    /**
     * Retrieves the green IT details of a server, including server metadata and metrics.
     *
     * @param serverId the unique identifier of the server whose green IT details are to be retrieved
     * @return a {@code ServerGreenItDetailDTO} containing the server's details, metric information,
     *         and green IT-related properties
     * @throws EntityNotFoundException if the server with the specified ID is not found
     */
    public GreenItServerDTO getServerGreenItDetail(final Long serverId) {
        final Server server = serverService.findById(serverId).orElseThrow(() -> new EntityNotFoundException("Server not found: " + serverId));
        final List<GreenItServerMetricsDTO> metricsDTOs = serverMetricsService
                .findByServerIdOrderByIdAsc(serverId)
                .stream()
                .map(m -> new GreenItServerMetricsDTO(m.getCreatedAt(), m.getCpuUtil(), m.getMemUsedPercent()))
                .toList();
        return new GreenItServerDTO(
                server.getId(),
                server.getCloud().getName(),
                server.getName(),
                server.getFqdn(),
                server.getPowerState(),
                server.getMemoryMb(),
                server.getMemoryMbPrev(),
                server.getMemoryMbChangeDate(),
                server.getMemoryMbChangeDatePrev(),
                server.getNumCpu(),
                server.getNumCpuPrev(),
                server.getNumCpuChangeDate(),
                server.getNumCpuChangeDatePrev(),
                server.getBootTime(),
                server.getGreenItShutdownChangePending(),
                server.getGreenItShutdownChangeRejectedDate(),
                server.getGreenItRightsizingChangePending(),
                server.getGreenItRightsizingChangeRejectedDate(),
                metricsDTOs
        );
    }

    /**
     * Retrieves a list of server IDs that have associated metrics and are enabled for Green IT.
     * The method utilizes the serverMetricsService to filter and obtain the relevant server IDs.
     *
     * @return a list of server IDs (as Long objects) that meet the criteria of having metrics and being Green IT enabled.
     */
    public List<Long> getServerIdsWithMetrics() {
        return serverMetricsService.findServerIdsWithMetricsAndGreenItEnabled();
    }

    public void processRightsizeRecommendations(final RightsizingRequestDTO rightsizingServerDTOList) {
        int count = 1;
        for (RightsizingServerDTO rightsizingServerDTO: rightsizingServerDTOList.servers()){
            final Server server = serverService.findById(rightsizingServerDTO.id()).orElseThrow(() -> new EntityNotFoundException("Server not found: " + rightsizingServerDTO.id()));
            serverService.updateRessourceRecommendations(rightsizingServerDTO.id(), rightsizingServerDTO.numCpu(), rightsizingServerDTO.memoryMb());

            // Process Rightsizing if 2 Weeks before the patchnight
            if(false && server.getPatchnightStartDate() != null
                    && server.getPatchnightStartDate().toLocalDate().minusDays(14).isEqual(LocalDate.now())
                    &&!excludeFromGreenIT(server)){
                processRightsizing(server, rightsizingServerDTO,count);
                System.out.println(server.getName());
                count++;
            }
        }
    }

    private boolean excludeFromGreenIT(final Server server){
        String serverName = server.getName();

        // not in patchnight
        if (server.getPatchnightIncluded()){
            return true;
        }

        // skip vms from the LHMS
        if (serverName.startsWith("lhms")) {
            return true;
        }

        //HSD und CTX Cluster
        if (server.getCluster().contains("CTX") || server.getCluster().contains("HSD")){
            return true;
        }

        // skip exclude List
        if (serverName.startsWith("anx") ||
                serverName.startsWith("cn-") ||
                serverName.startsWith("pgtestdpk") ||
                serverName.startsWith("pg915testdpk") ||
                serverName.startsWith("pgc15l9mdpk") ||
                serverName.startsWith("pg179tcdpk") ||
                serverName.startsWith("geogdi2dpkv") ||
                serverName.startsWith("gdisensordp") ||
                serverName.startsWith("geogdi2dp") ||
                serverName.startsWith("swimcdp") ||
                serverName.startsWith("kofax") ||
                serverName.startsWith("stagokkop")) {

            return true;
        }
        return false;
    }

    private void processRightsizing (final Server server, final RightsizingServerDTO rightsizingServerDTO, final int count) {
        OffsetDateTime startTime = server.getPatchnightStartDate().plusMinutes(10*(count/40));


        final GreenItRightsizing rightsizing = GreenItRightsizing.builder()
                .vmName(server.getName())
                .startTime(startTime)
                .cpuCurrent(server.getNumCpu())
                .cpuNew(rightsizingServerDTO.numCpu())
                .ramCurrent(server.getMemoryMb())
                .ramNew(rightsizingServerDTO.memoryMb() * MB_PER_GB)
                .serverUuid(server.getUuid())
                .vcenterShortCode(" ") //TODO muss weg
                .server(server)
                .build();

        processGreenItOperation(
                rightsizing,
                server,
                greenItRightsizingService::save,
                GreenItRightsizing::setStatus,
                GreenItRightsizing::setAppservice,
                server.getGreenItRightsizingChangeRejectedDate(),
                server.getGreenItRightsizingChangePending(),
                "rightsizing",
                jobService::createGreenItRightsizingJob
        );
    }
}
