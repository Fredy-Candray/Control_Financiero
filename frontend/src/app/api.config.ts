const host = window.location.hostname;

// En Dev Tunnels, call the Angular origin so its dev-server proxy can relay
// /api to the backend on port 8081 without exposing that port or triggering CORS.
const configuredApi = document.querySelector<HTMLMetaElement>('meta[name="financial-api-base"]')?.content;
export const API_BASE_URL = configuredApi
  || (host.endsWith('.use2.devtunnels.ms') || window.location.port !== '4200'
    ? `${window.location.origin}/api`
    : `${window.location.protocol}//${host}:8081/api`);
