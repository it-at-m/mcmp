<template>
  <common-card :title="title ?? ''">
    <template #toolbar-actions>
      <v-btn
        v-if="showRefresh"
        @click="$emit('refresh')"
        :icon="refreshIcon"
      />
    </template>
    <slot />
    <component
      :is="serverSide ? VDataTableServer : VDataTable"
      v-model:expanded="expanded"
      :headers="headers"
      :items="formattedHistory"
      :items-per-page="itemsPerPage"
      :page="page"
      :items-length="serverSide ? totalItems : undefined"
      class="elevation-1"
      :loading="loading"
      show-expand
      :items-per-page-options="itemsPerPageOptions"
      @update:page="$emit('update:page', $event)"
      @update:items-per-page="$emit('update:itemsPerPage', $event)"
    >
      <template #headers>
        <tr>
          <th
            v-for="header in headers"
            :key="header.key"
          >
            <span
              @click="setSort(header.key)"
              @keyup.space="setSort(header.key)"
              :aria-label="'sortiere nach ' + header.key"
              style="cursor: pointer; display: flex; align-items: center"
              tabindex="0"
              :aria-sort="
                sortBy === header.key
                  ? sortDesc
                    ? 'descending'
                    : 'ascending'
                  : 'none'
              "
            >
              {{ header.title }}
              <v-icon
                :style="{
                  visibility: sortBy === header.key ? 'visible' : 'hidden',
                }"
              >
                {{ sortDesc ? mdiArrowDown : mdiArrowUp }}
              </v-icon>
            </span>
          </th>
        </tr>
      </template>
      <template #no-data>
        <v-row>
          <v-col> Keine History-Einträge gefunden.</v-col>
        </v-row>
      </template>
      <template #expanded-row="{ item, columns }">
        <tr>
          <td :colspan="columns.length">
            <v-card
              flat
              class="pa-3"
            >
              <div class="details-table">
                <common-card
                  title="Job Details"
                  :is-default-expanded="true"
                  top-margin="0"
                >
                  <template #prepend-title>
                    <job-status-icon
                      :status="item.status"
                      class="mr-2"
                    />
                  </template>
                  <div class="detail-content">
                    <div class="detail-row">
                      <span class="detail-label">Beschreibung:</span>
                      <span
                        class="detail-value"
                        style="font-weight: bold"
                        >{{ item.description }}</span
                      >
                    </div>
                    <div class="detail-row">
                      <span class="detail-label">Job ID:</span>
                      <span class="detail-value">{{ item.id }}</span>
                    </div>
                    <div class="detail-row">
                      <span class="detail-label">Status:</span>
                      <div class="detail-value">
                        <workflow-status :job="item" />
                      </div>
                    </div>
                    <div class="detail-row">
                      <span class="detail-label">Erstellt am:</span>
                      <span class="detail-value">{{
                        item.createdAtFormatted
                      }}</span>
                    </div>
                    <div
                      class="detail-row"
                      v-if="item.userName"
                    >
                      <span class="detail-label">Durchgeführt von:</span>
                      <span class="detail-value">{{ item.userName }}</span>
                    </div>
                    <div
                      class="detail-row"
                      v-if="item.serverId"
                    >
                      <span class="detail-label">Server:</span>
                      <span class="detail-value">
                        <a :href="`/#/server/${item.serverId}`">
                          {{ item.serverName }}
                        </a>
                      </span>
                    </div>
                    <div
                      class="detail-row"
                      v-if="item.appServiceId"
                    >
                      <span class="detail-label">Anwendungsservice:</span>
                      <span class="detail-value">
                        <a :href="`/#/appservice/${item.appServiceId}`">
                          {{ item.appServiceName }}
                        </a>
                      </span>
                    </div>
                    <!-- Incidents Tabelle -->
                    <div
                      class="detail-row"
                      v-if="jobIncidents[item.id]?.length"
                      style="align-items: center"
                    >
                      <span class="detail-label">Incidents:</span>
                      <div class="detail-value">
                        <v-data-table
                          :headers="incidentHeaders"
                          :items="jobIncidents[item.id]"
                          :loading="incidentsLoading[item.id]"
                          density="compact"
                          class="elevation-0 border links"
                          hide-default-footer
                          :items-per-page="-1"
                        >
                          <template #item.incidentNumber="{ item: incident }">
                            <a
                              :href="incident.incidentLink"
                              target="_blank"
                              rel="noopener noreferrer"
                            >
                              {{ incident.incidentNumber }}
                            </a>
                          </template>
                          <template #item.status="{ item: incident }">
                            <incident-status-icon :status="incident.status" />
                          </template>
                          <template #item.success="{ item: incident }">
                            <v-icon
                              v-if="
                                incident.success !== null &&
                                incident.success !== undefined
                              "
                              :icon="
                                incident.success
                                  ? mdiCheckCircle
                                  : mdiCloseCircle
                              "
                              :color="incident.success ? 'success' : 'error'"
                            />
                          </template>
                        </v-data-table>
                      </div>
                    </div>
                  </div>
                </common-card>

                <!-- ServiceNow Bereich -->
                <common-card
                  v-if="item.changeRequired"
                  title="ServiceNow"
                  :is-default-expanded="false"
                  top-margin="0"
                >
                  <template #prepend-title>
                    <job-status-icon
                      :status="item.changeStatus"
                      class="mr-2"
                    />
                  </template>
                  <div class="detail-content">
                    <div
                      class="detail-row"
                      v-if="item.changeStartDate"
                    >
                      <span class="detail-label">geplante Startzeit:</span>
                      <span class="detail-value">{{
                        item.changeStartDateFormatted
                      }}</span>
                    </div>
                    <div class="detail-row">
                      <span class="detail-label">ServiceNow Status:</span>
                      <span class="detail-value"
                        ><job-status-icon :status="item.changeStatus"
                      /></span>
                    </div>
                    <div class="detail-row">
                      <span class="detail-label">Change:</span>
                      <span class="detail-value">
                        <a
                          :href="`${item.changeLink}`"
                          target="_blank"
                          rel="noopener noreferrer"
                        >
                          {{ item.changeNumber }}
                        </a>
                      </span>
                    </div>
                    <div class="detail-row">
                      <span class="detail-label">ServiceNow:</span>
                      <span class="detail-value">{{
                        item.snowApiDescription
                      }}</span>
                    </div>
                    <div
                      class="detail-row"
                      v-if="item.changeError"
                    >
                      <span class="detail-label">Fehlermeldung:</span>
                      <span class="detail-value">{{ item.changeError }}</span>
                    </div>
                  </div>
                </common-card>

                <!-- AWX Bereich -->
                <common-card
                  v-if="item.awxJobEnabled"
                  title="AWX"
                  :is-default-expanded="false"
                  top-margin="0"
                >
                  <template #prepend-title>
                    <job-status-icon
                      :status="item.awxStatus"
                      class="mr-2"
                    />
                  </template>
                  <div
                    class="detail-content"
                    v-if="item.awxTemplateType === 'template'"
                  >
                    <div class="detail-row">
                      <span class="detail-label">AWX:</span>
                      <span class="detail-value">{{
                        item.awxApiDescription
                      }}</span>
                    </div>
                    <div class="detail-row">
                      <span class="detail-label">Job:</span>
                      <span class="detail-value links">
                        <a
                          :href="`${item.awxJobLink}`"
                          target="_blank"
                          rel="noopener noreferrer"
                        >
                          {{ item.awxJobId }}
                        </a>
                      </span>
                    </div>
                    <div class="detail-row">
                      <span class="detail-label">Template Typ:</span>
                      <span class="detail-value">{{
                        item.awxTemplateType
                      }}</span>
                    </div>
                    <div class="detail-row">
                      <span class="detail-label">Template ID:</span>
                      <span class="detail-value links">
                        <a
                          v-if="item.awxTemplateLink"
                          :href="item.awxTemplateLink"
                          target="_blank"
                          rel="noopener noreferrer"
                        >
                          {{ item.awxTemplateId }}
                        </a>
                        <span v-else>{{ item.awxTemplateId }}</span>
                      </span>
                    </div>
                    <div
                      class="detail-row"
                      v-if="item.awxStartDate"
                    >
                      <span class="detail-label">AWX-Startzeit:</span>
                      <span class="detail-value">{{
                        item.awxStartDateFormatted
                      }}</span>
                    </div>
                    <div
                      class="detail-row"
                      v-if="item.awxEndDate"
                    >
                      <span class="detail-label">AWX-Endzeit:</span>
                      <span class="detail-value">{{
                        item.awxEndDateFormatted
                      }}</span>
                    </div>
                    <div
                      class="detail-row"
                      v-if="item.awxJobDuration"
                    >
                      <span class="detail-label">AWX-Laufzeit:</span>
                      <span class="detail-value">{{
                        formatDuration(item.awxJobDuration)
                      }}</span>
                    </div>
                    <div
                      class="detail-row"
                      v-if="item.awxError"
                    >
                      <span class="detail-label">Fehlermeldung:</span>
                      <span class="detail-value">{{ item.awxError }}</span>
                    </div>
                    <div
                      class="detail-row"
                      v-if="item.awxJobOrg"
                    >
                      <span class="detail-label">AWX Organisation:</span>
                      <span class="detail-value">{{ item.awxJobOrg }}</span>
                    </div>
                    <div class="detail-row">
                      <span class="detail-label">AWX Gesamtstatus:</span>
                      <div class="detail-value">
                        <job-status-icon :status="item.awxStatus" />
                      </div>
                    </div>
                    <div
                      class="detail-row"
                      v-if="item.awxJobStatus"
                    >
                      <span class="detail-label">AWX Job Status:</span>
                      <div class="detail-value">
                        <job-status-icon :status="item.awxJobStatus" />
                      </div>
                    </div>
                    <div
                      class="detail-row"
                      v-if="item.awxJobFailed !== null"
                    >
                      <span class="detail-label">Failed:</span>
                      <span class="detail-value">{{ item.awxJobFailed }} </span>
                    </div>
                    <div
                      class="detail-row"
                      v-if="item.awxJobReturnCompleted !== null"
                    >
                      <span class="detail-label">Job Result Completed:</span>
                      <span class="detail-value"
                        >{{ item.awxJobReturnCompleted }}
                      </span>
                    </div>
                    <div
                      class="detail-row"
                      v-if="item.awxJobReturnMessage"
                    >
                      <span class="detail-label">Job Result Message:</span>
                      <pre class="detail-value description-text">{{
                        item.awxJobReturnMessage
                      }}</pre>
                    </div>
                    <div
                      class="detail-row"
                      v-if="item.awxJobReturnData"
                    >
                      <span class="detail-label">Job Result Data:</span>
                      <pre class="detail-value description-text">{{
                        item.awxJobReturnData
                      }}</pre>
                    </div>
                    <div
                      class="detail-row"
                      v-if="item.awxJobErrorMessage"
                    >
                      <span class="detail-label">AWX Fehlermeldung:</span>
                      <pre class="detail-value description-text">{{
                        item.awxJobErrorMessage
                      }}</pre>
                    </div>
                    <div
                      class="detail-row"
                      v-if="item.awxVariables"
                    >
                      <span class="detail-label">AWX Launch Request:</span>
                      <pre class="detail-value description-text">{{
                        item.awxVariables
                      }}</pre>
                    </div>
                    <div
                      class="detail-row"
                      v-if="!item.awxVariables && item.awxExtraVars"
                    >
                      <span class="detail-label">AWX ExtraVars:</span>
                      <span class="detail-value description-text">
                        <pre>{{ item.awxExtraVars }}</pre>
                      </span>
                    </div>
                    <div
                      class="detail-row"
                      v-if="item.awxJobArtifacts"
                    >
                      <span class="detail-label">Artifacts:</span>
                      <pre class="detail-value description-text">{{
                        item.awxJobArtifacts
                      }}</pre>
                    </div>
                  </div>

                  <!-- Job Nodes Tabelle -->
                  <div
                    v-if="item.awxTemplateType === 'workflow'"
                    class="mt-1"
                  >
                    <v-data-table
                      v-model:expanded="expandedNodes[item.id]"
                      :headers="nodeHeaders"
                      :items="jobNodes[item.id] || []"
                      :loading="nodesLoading[item.id]"
                      density="compact"
                      class="elevation-0 border links"
                      hide-default-footer
                      :items-per-page="-1"
                      show-expand
                      item-value="nodeAlias"
                    >
                      <template
                        #item.data-table-expand="{
                          internalItem,
                          isExpanded,
                          toggleExpand,
                          item: node,
                        }"
                      >
                        <v-btn
                          v-if="node.jobStatus !== 'not executed'"
                          :icon="
                            isExpanded(internalItem) ? '$collapse' : '$expand'
                          "
                          variant="text"
                          density="comfortable"
                          @click="toggleExpand(internalItem)"
                        ></v-btn>
                      </template>
                      <template #item.nodeAlias="{ item: node }">
                        <div
                          :style="{
                            paddingLeft:
                              Math.max(0, (node.jobDepth || 0) - 1) * 20 + 'px',
                          }"
                        >
                          <span v-if="(node.jobDepth || 0) > 0">└─ </span>
                          <a
                            v-if="node.jobAwxLink"
                            :href="node.jobAwxLink"
                            target="_blank"
                            rel="noopener noreferrer"
                          >
                            {{ node.nodeAlias }}
                          </a>
                          <span v-else>{{ node.nodeAlias }}</span>
                        </div>
                      </template>
                      <template #item.jobStatus="{ item: node }">
                        <div style="display: flex; align-items: center">
                          <job-status-icon :status="node.jobStatus" />
                          <v-tooltip
                            v-if="node.jobIsRootCause"
                            text="Root Cause"
                          >
                            <template #activator="{ props }">
                              <v-icon
                                v-bind="props"
                                :icon="mdiCheckCircle"
                                color="error"
                                class="ml-1"
                              />
                            </template>
                          </v-tooltip>
                        </div>
                      </template>
                      <template #item.jobReturnMessage="{ item: node }">
                        <v-tooltip
                          v-if="
                            node.jobReturnMessage &&
                            node.jobReturnMessage.length > 10
                          "
                          :text="node.jobReturnMessage"
                        >
                          <template #activator="{ props }">
                            <span v-bind="props">{{
                              truncateString(node.jobReturnMessage, 10)
                            }}</span>
                          </template>
                        </v-tooltip>
                        <span v-else>{{ node.jobReturnMessage }}</span>
                      </template>
                      <template #item.jobErrorMessage="{ item: node }">
                        <v-tooltip
                          v-if="
                            node.jobErrorMessage &&
                            node.jobErrorMessage.length > 10
                          "
                          :text="node.jobErrorMessage"
                        >
                          <template #activator="{ props }">
                            <span v-bind="props">{{
                              truncateString(node.jobErrorMessage, 10)
                            }}</span>
                          </template>
                        </v-tooltip>
                        <span v-else>{{ node.jobErrorMessage }}</span>
                      </template>
                      <template #item.jobStarted="{ item: node }">
                        {{ formatToBerlinDateTime(node.jobStarted) }}
                      </template>
                      <template #item.jobFinished="{ item: node }">
                        {{ formatToBerlinDateTime(node.jobFinished) }}
                      </template>
                      <template #item.jobDuration="{ item: node }">
                        {{ formatDuration(node.jobDuration) }}
                      </template>
                      <template #item.jobReturnCompleted="{ item: node }">
                        <v-tooltip
                          v-if="node.jobReturnCompleted !== null"
                          :text="node.jobReturnCompleted ? 'true' : 'false'"
                        >
                          <template #activator="{ props }">
                            <v-icon
                              v-bind="props"
                              :icon="
                                node.jobReturnCompleted
                                  ? mdiCheckCircle
                                  : mdiCloseCircle
                              "
                              :color="
                                node.jobReturnCompleted ? 'success' : 'error'
                              "
                            />
                          </template>
                        </v-tooltip>
                      </template>
                      <template
                        #expanded-row="{ item: node, columns: nodeColumns }"
                      >
                        <tr>
                          <td :colspan="nodeColumns.length">
                            <div class="pa-4 node-details-expanded">
                              <div
                                class="detail-row"
                                v-if="node.awxDescription"
                              >
                                <span class="detail-label">AWX:</span>
                                <span class="detail-value">{{
                                  node.awxDescription
                                }}</span>
                              </div>
                              <div class="detail-row">
                                <span class="detail-label">Job:</span>
                                <span class="detail-value links">
                                  <a
                                    v-if="node.jobAwxLink"
                                    :href="node.jobAwxLink"
                                    target="_blank"
                                    rel="noopener noreferrer"
                                  >
                                    {{ node.jobId }}
                                  </a>
                                  <span v-else>{{ node.jobId }}</span>
                                </span>
                              </div>
                              <div class="detail-row">
                                <span class="detail-label">Template ID:</span>
                                <span class="detail-value links">
                                  <a
                                    v-if="node.templateLink"
                                    :href="node.templateLink"
                                    target="_blank"
                                    rel="noopener noreferrer"
                                  >
                                    {{ node.templateId }}
                                  </a>
                                  <span v-else>{{ node.templateId }}</span>
                                </span>
                              </div>
                              <div class="detail-row">
                                <span class="detail-label">AWX-Startzeit:</span>
                                <span class="detail-value">
                                  {{
                                    formatToBerlinDateTime(node.jobStarted)
                                  }}</span
                                >
                              </div>
                              <div class="detail-row">
                                <span class="detail-label">AWX-Endzeit:</span>
                                <span class="detail-value">
                                  {{
                                    formatToBerlinDateTime(node.jobFinished)
                                  }}</span
                                >
                              </div>
                              <div class="detail-row">
                                <span class="detail-label">AWX-Laufzeit:</span>
                                <span class="detail-value">{{
                                  formatDuration(node.jobDuration)
                                }}</span>
                              </div>
                              <div
                                class="detail-row"
                                v-if="node.jobDepth === 0"
                              >
                                <span class="detail-label"
                                  >AWX Gesamtstatus:</span
                                >
                                <div class="detail-value">
                                  <job-status-icon :status="node.awxStatus" />
                                </div>
                              </div>
                              <div class="detail-row">
                                <span class="detail-label"
                                  >AWX Job Status:</span
                                >
                                <div class="detail-value">
                                  <job-status-icon :status="node.jobStatus" />
                                </div>
                              </div>
                              <div class="detail-row">
                                <span class="detail-label"
                                  >Job Result Completed:</span
                                >
                                <span class="detail-value">{{
                                  node.jobReturnCompleted
                                }}</span>
                              </div>
                              <div class="detail-row">
                                <span class="detail-label"
                                  >Job Result Message:</span
                                >
                                <pre class="description-text">{{
                                  node.jobReturnMessage
                                }}</pre>
                              </div>
                              <div class="detail-row">
                                <span class="detail-label"
                                  >Job Result Data:</span
                                >
                                <pre class="description-text">{{
                                  node.jobReturnData
                                }}</pre>
                              </div>
                              <div
                                class="detail-row"
                                v-if="node.jobErrorMessage"
                              >
                                <span class="detail-label">Error:</span>
                                <pre class="description-text">{{
                                  node.jobErrorMessage
                                }}</pre>
                              </div>
                              <div
                                v-if="
                                  node.jobDepth === 0 && node.awxLaunchRequest
                                "
                                class="detail-row"
                              >
                                <span class="detail-label"
                                  >AWX Launch Request:</span
                                >
                                <pre class="description-text">{{
                                  node.awxLaunchRequest
                                }}</pre>
                              </div>
                              <div
                                class="detail-row"
                                v-if="
                                  node.jobExtraVars &&
                                  ((node.jobDepth === 0 &&
                                    !node.awxLaunchRequest) ||
                                    (node.jobDepth ?? 0) > 0)
                                "
                              >
                                <span class="detail-label">ExtraVars:</span>
                                <pre class="description-text">{{
                                  node.jobExtraVars
                                }}</pre>
                              </div>
                              <div
                                class="detail-row"
                                v-if="node.jobArtifacts"
                              >
                                <span class="detail-label">Artifacts:</span>
                                <pre class="description-text">{{
                                  node.jobArtifacts
                                }}</pre>
                              </div>
                            </div>
                          </td>
                        </tr>
                      </template>
                    </v-data-table>
                  </div>
                </common-card>

                <!-- QuickDiscovery Bereich -->
                <common-card
                  v-if="item.quickdiscovery || item.serverInstallation"
                  title="QuickDiscovery"
                  :is-default-expanded="false"
                  top-margin="0"
                >
                  <template #prepend-title>
                    <job-status-icon
                      :status="item.quickdiscoveryStatus"
                      class="mr-2"
                    />
                  </template>
                  <div class="detail-content">
                    <div class="detail-row">
                      <span class="detail-label">Hostname:</span>
                      <span class="detail-value">{{ item.hostname }}</span>
                    </div>
                    <div class="detail-row">
                      <span class="detail-label">IP:</span>
                      <span class="detail-value">{{ item.ip }}</span>
                    </div>
                    <div class="detail-row">
                      <span class="detail-label">Status:</span>
                      <span class="detail-value"
                        ><job-status-icon :status="item.quickdiscoveryStatus"
                      /></span>
                    </div>
                    <div
                      class="detail-row"
                      v-if="item.quickdiscoveryError"
                    >
                      <span class="detail-label">Fehlermeldung:</span>
                      <pre class="detail-value">{{
                        item.quickdiscoveryError
                      }}</pre>
                    </div>
                    <div class="detail-row">
                      <span class="detail-label">Anzahl Fehler:</span>
                      <span class="detail-value">{{
                        item.quickdiscoveryErrorCounter
                      }}</span>
                    </div>
                    <div class="detail-row">
                      <span class="detail-label">CI-Name:</span>
                      <span class="detail-value">{{
                        item.quickdiscoveryCiName
                      }}</span>
                    </div>
                    <div class="detail-row">
                      <span class="detail-label">CI-SysID:</span>
                      <span class="detail-value">{{
                        item.quickdiscoveryCiSysid
                      }}</span>
                    </div>
                  </div>
                </common-card>

                <!-- Tagging Bereich -->
                <common-card
                  v-if="item.serverInstallation"
                  title="Tagging"
                  :is-default-expanded="false"
                  top-margin="0"
                >
                  <template #prepend-title>
                    <job-status-icon
                      :status="item.taggingStatus"
                      class="mr-2"
                    />
                  </template>
                  <div class="detail-content">
                    <div class="detail-row">
                      <span class="detail-label">Status:</span>
                      <span class="detail-value"
                        ><job-status-icon :status="item.taggingStatus"
                      /></span>
                    </div>
                    <div
                      class="detail-row"
                      v-if="item.taggingError"
                    >
                      <span class="detail-label">Fehlermeldung:</span>
                      <pre class="detail-value">{{ item.taggingError }}</pre>
                    </div>
                  </div>
                </common-card>
              </div>
            </v-card>
          </td>
        </tr>
      </template>
      <!-- eslint-disable-next-line vue/valid-v-slot -->
      <template #item.status="{ item }">
        <workflow-status
          :job="item"
          hide-labels
        />
      </template>
      <template #item.id="{ item }">
        <div style="display: flex; align-items: center">
          {{ item.id }}
          <v-tooltip
            :text="item.awxTemplateType === 'workflow' ? 'Workflow' : 'Job'"
          >
            <template #activator="{ props }">
              <v-icon
                v-bind="props"
                size="x-small"
                class="ml-1"
                :icon="item.awxTemplateType === 'workflow' ? mdiCogs : mdiCog"
              />
            </template>
          </v-tooltip>
        </div>
      </template>
      <template #item.createdAt="{ item }">
        {{ item.createdAtFormatted }}
      </template>
      <!-- eslint-disable-next-line vue/valid-v-slot -->
      <template #item.changeStartDate="{ item }">
        {{ item.changeStartDateFormatted }}
      </template>
    </component>
  </common-card>
</template>

<script setup lang="ts">
import type JobIncidentSummary from "@/types/JobIncidentSummary";
import type JobList from "@/types/JobList";
import type JobNodeHierarchy from "@/types/JobNodeHierarchy";

import {
  mdiArrowDown,
  mdiArrowUp,
  mdiCheckCircle,
  mdiCloseCircle,
  mdiCog,
  mdiCogs,
  mdiRefresh,
} from "@mdi/js";
import { computed, ref, watch } from "vue";
import { VDataTable, VDataTableServer } from "vuetify/components";

import jobService from "@/api/jobService";
import CommonCard from "@/components/common/CommonCard.vue";
import IncidentStatusIcon from "@/components/common/IncidentStatusIcon.vue";
import JobStatusIcon from "@/components/common/JobStatusIcon.vue";
import WorkflowStatus from "@/components/common/WorkflowStatus.vue";
import { formatDuration, formatToBerlinDateTime } from "@/util/formatter";

const props = withDefaults(
  defineProps<{
    type: string;
    history: JobList[];
    loading: boolean;
    headers: { title: string; key: string; [key: string]: unknown }[];
    page: number;
    itemsPerPage: number;
    serverSide?: boolean;
    totalItems?: number;
    itemsPerPageOptions?: number[];
    title?: string;
    showRefresh?: boolean;
    refreshIcon?: string;
  }>(),
  {
    serverSide: false,
    totalItems: 0,
    itemsPerPageOptions: () => [10, 25, 50, 100],
    showRefresh: false, // Neu
    refreshIcon: mdiRefresh, // Neu
  }
);

const emit = defineEmits<{
  (e: "update:page" | "update:itemsPerPage", value: number): void;
  (e: "update:sortBy", sortBy: string): void;
  (e: "update:sortDesc", sortDesc: boolean): void;
  (e: "update:sort", sort: { by: string; desc: boolean }): void;
  (e: "refresh"): void; // Neu
}>();

const expanded = ref<string[]>([]);
const expandedNodes = ref<Record<number, string[]>>({});

const formattedHistory = computed(() =>
  props.history.map((job) => ({
    ...job,
    createdAtFormatted: job.createdAt
      ? formatToBerlinDateTime(job.createdAt)
      : "",
    createdAtDate: job.createdAt ? new Date(job.createdAt) : null,
    changeStartDateFormatted: job.changeStartDate
      ? formatToBerlinDateTime(job.changeStartDate)
      : "",
    awxStartDateFormatted: job.awxStartDate
      ? formatToBerlinDateTime(job.awxStartDate)
      : "",
    awxEndDateFormatted: job.awxEndDate
      ? formatToBerlinDateTime(job.awxEndDate)
      : "",
  }))
);

const nodeHeaders = [
  { title: "Name", key: "nodeAlias" },
  { title: "Typ", key: "templateType" },
  { title: "Org", key: "jobOrg" },
  { title: "Status", key: "jobStatus" },
  { title: "Completed", key: "jobReturnCompleted" },
  { title: "Message", key: "jobReturnMessage" },
  { title: "Error", key: "jobErrorMessage" },
  //{ title: "Start", key: "jobStarted" },
  //{ title: "Ende", key: "jobFinished" },
  { title: "Dauer", key: "jobDuration" },
] as const;

const incidentHeaders = [
  { title: "Incident Nummer", key: "incidentNumber" },
  { title: "Quelle", key: "sourceType" },
  { title: "Status", key: "status" },
  { title: "Erfolgreich", key: "success" },
  { title: "Abschlussnotiz", key: "closeNotes" },
] as const;

const jobNodes = ref<Record<number, JobNodeHierarchy[]>>({});
const nodesLoading = ref<Record<number, boolean>>({});

const jobIncidents = ref<Record<number, JobIncidentSummary[]>>({});
const incidentsLoading = ref<Record<number, boolean>>({});

const sortBy = ref<string>("id");
const sortDesc = ref<boolean>(true);

watch(expanded, (newExpanded) => {
  newExpanded.forEach((id) => {
    const numericId = typeof id === "number" ? id : parseInt(id, 10);
    const jobItem = props.history.find((h) => h.id === numericId);

    if (jobItem) {
      if (
        jobItem.awxTemplateType === "workflow" &&
        jobItem.awxStatus !== "running" &&
        !jobNodes.value[numericId]
      ) {
        loadNodes(numericId);
      }

      if (!jobIncidents.value[numericId]) {
        loadIncidents(numericId);
      }
    }
  });
});

async function loadNodes(jobId: number) {
  const loadingRef = ref(false);
  nodesLoading.value[jobId] = true;
  try {
    jobNodes.value[jobId] = await jobService.getJobHierarchy(loadingRef, jobId);
  } catch (error) {
    console.debug("Fehler beim Laden der Job-Nodes:", error);
  } finally {
    nodesLoading.value[jobId] = false;
  }
}

async function loadIncidents(jobId: number) {
  const loadingRef = ref(false);
  incidentsLoading.value[jobId] = true;
  try {
    jobIncidents.value[jobId] = await jobService.getJobIncidents(
      loadingRef,
      jobId
    );
  } catch (error) {
    console.debug("Fehler beim Laden der Job-Incidents:", error);
  } finally {
    incidentsLoading.value[jobId] = false;
  }
}

function truncateString(str: string | null, num: number) {
  if (!str) return "";
  if (str.length <= num) {
    return str;
  }
  return str.slice(0, num) + "...";
}

function setSort(key: string) {
  if (sortBy.value === key) {
    if (sortDesc.value) {
      sortBy.value = "";
      sortDesc.value = false;
    } else {
      sortDesc.value = true;
    }
  } else {
    sortBy.value = key;
    sortDesc.value = false;
  }
  emit("update:sortBy", sortBy.value);
  emit("update:sortDesc", sortDesc.value);
  emit("update:sort", { by: sortBy.value, desc: sortDesc.value });
}
</script>

<!--suppress CssUnresolvedCustomProperty -->
<style scoped>
.details-table {
  width: 100%;
}

.detail-section-title {
  font-weight: bold;
  margin-bottom: 8px;
  color: rgb(var(--v-theme-primary));
}

.detail-content {
  margin-left: 16px;
}

.detail-row {
  display: flex;
  align-items: flex-start;
  margin-bottom: 0;
  min-height: 16px;
}

.detail-row:last-child {
  margin-bottom: 0;
}

.detail-label {
  width: 200px;
  min-width: 200px;
  text-align: right;
  font-weight: bold;
  padding-right: 16px;
  flex-shrink: 0;
}

.detail-value {
  flex: 1;
  text-align: left;
}

.description-text {
  white-space: pre-wrap;
  word-break: break-word;
}

.detail-value a {
  color: #1976d2;
  text-decoration: none;
}

.detail-value a:hover {
  text-decoration: underline;
}

.icon-grey-bg {
  background-color: grey;
  color: rgb(var(--v-theme-bg_icon)) !important;
  border-radius: 50%;
  font-size: 18px;
  text-align: center;
}

.icon-blue-bg {
  background-color: rgb(var(--v-theme-accent));
  color: rgb(var(--v-theme-bg_icon)) !important;
  border-radius: 50%;
  font-size: 18px;
  text-align: center;
}

.status-icon {
  display: flex;
  background-color: rgb(var(--v-theme-bg_icon));
  align-items: center;
  justify-content: center;
  width: 15px !important;
  flex-shrink: 0;
}

.round-icon {
  height: 15px !important;
  border-radius: 50%;
}

.square-icon {
  height: 12px !important;
}

.links a,
.links a:visited,
.links a:hover,
.links a:active {
  color: rgb(var(--v-theme-link));
  text-decoration: none;
}

:deep(.v-data-table.border td),
:deep(.v-data-table.border th) {
  padding: 0 2px !important;
}

.node-details-expanded {
  background-color: rgba(var(--v-theme-on-surface), 0.05);
  color: rgb(var(--v-theme-on-surface));
}
</style>
