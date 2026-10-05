const host = window.location.hostname;

// En Dev Tunnels, call the Angular origin so its dev-server proxy can relay
// /api to the backend on port 8081 without exposing that port or triggering CORS.
export const API_BASE_URL = host.endsWith('.use2.devtunnels.ms')
  ? `${window.location.origin}/api`
  : `${window.location.protocol}//${host}:8081/api`;
