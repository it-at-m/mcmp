export default interface JobIncidentSummary {
  status: string;
  sourceType: string;
  incidentNumber: string;
  incidentLink: string;
  success: boolean;
  closeNotes: string;
}
