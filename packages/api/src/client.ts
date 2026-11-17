import createClient from 'openapi-fetch';
import type { paths } from './schema';

<<<<<<< HEAD
const BASE_URL =
  process.env.EXPO_PUBLIC_API_BASE_URL ?? process.env.API_BASE_URL ?? 'http://localhost/api/v1';
=======
declare const process: {
  env?: {
    API_BASE_URL?: string;
    EXPO_PUBLIC_API_BASE_URL?: string;
  };
};

const BASE_URL =
  typeof process !== 'undefined' && process.env?.API_BASE_URL
    ? process.env.API_BASE_URL
    : 'http://localhost/api/v1';
>>>>>>> e5d649b0c7c64dde620632afc973aea880881432

export const client = createClient<paths>({ baseUrl: BASE_URL });

let _token: string | null = null;

/** Call after login to attach the Bearer token to all requests. */
export function setAuthToken(token: string | null) {
  _token = token;
  client.use({
    async onRequest(req) {
      if (_token) req.headers.set('Authorization', `Bearer ${_token}`);
      return req;
    },
  });
}
