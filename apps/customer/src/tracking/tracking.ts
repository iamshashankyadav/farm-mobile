/** Pure helpers for the delivery tracking screen (no React, so they are unit tested). */

/** GET /logistics/orders/{id}/track (logistics service). Everything but orderId can be missing. */
export type Track = {
  orderId: string;
  poolId?: string | null;
  status?: string | null; // POOLED, IN_TRANSIT or DELIVERED once the order is in a pool
  stop?: { lat: number; lng: number; address?: string | null } | null;
  partnerName?: string | null;
  partnerPhone?: string | null;
  partnerLat?: number | null;
  partnerLng?: number | null;
  partnerUpdatedAt?: string | null;
  eta?: string | null;
};

export type LatLng = { lat: number; lng: number };

/** What the customer sees: where the order is on its way. */
export type Phase = 'PREPARING' | 'POOLED' | 'IN_TRANSIT' | 'DELIVERED' | 'CANCELLED';

const ORDER_PHASES: Record<string, Phase> = {
  POOLED: 'POOLED',
  IN_TRANSIT: 'IN_TRANSIT',
  DELIVERED: 'DELIVERED',
  COMPLETED: 'DELIVERED',
  CANCELLED: 'CANCELLED',
};

/**
 * The order service is the source of truth for the status; the logistics track adds the live
 * detail. Logistics can be ahead of the order (events travel through the broker), so the further
 * of the two wins, except for a cancelled order.
 */
export function phaseOf(orderStatus: string, track: Track | null): Phase {
  const fromOrder = ORDER_PHASES[orderStatus] ?? 'PREPARING';
  if (fromOrder === 'CANCELLED') return fromOrder;
  const rank: Phase[] = ['PREPARING', 'POOLED', 'IN_TRANSIT', 'DELIVERED'];
  const fromTrack = ORDER_PHASES[track?.status ?? ''] ?? 'PREPARING';
  return rank.indexOf(fromTrack) > rank.indexOf(fromOrder) ? fromTrack : fromOrder;
}

export const PHASE_TEXT: Record<Phase, string> = {
  PREPARING: 'Your order is being prepared',
  POOLED: 'A delivery partner will collect your order',
  IN_TRANSIT: 'Your order is on its way',
  DELIVERED: 'Delivered',
  CANCELLED: 'This order was cancelled',
};

/** The customer can rate once the order is COMPLETED and has not been rated yet. */
export function canRate(orderStatus: string, rated: boolean): boolean {
  return orderStatus === 'COMPLETED' && !rated;
}

/** Poll the track while the order can still move; stop when it is over. */
export function shouldPoll(phase: Phase): boolean {
  return phase !== 'DELIVERED' && phase !== 'CANCELLED';
}

export function hasPartnerPosition(track: Track | null): track is Track & LatLngPartner {
  return typeof track?.partnerLat === 'number' && typeof track.partnerLng === 'number';
}
type LatLngPartner = { partnerLat: number; partnerLng: number };

export function haversineKm(a: LatLng, b: LatLng): number {
  const rad = (d: number) => (d * Math.PI) / 180;
  const dLat = rad(b.lat - a.lat);
  const dLng = rad(b.lng - a.lng);
  const h =
    Math.sin(dLat / 2) ** 2 + Math.cos(rad(a.lat)) * Math.cos(rad(b.lat)) * Math.sin(dLng / 2) ** 2;
  return 6371 * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
}

export function distanceLabel(km: number): string {
  return km < 1 ? `${Math.round(km * 1000)} m` : `${km.toFixed(1)} km`;
}

/** "in 12 min", "due now" or "" when there is no ETA. */
export function etaLabel(eta: string | null | undefined, now: Date): string {
  if (!eta) return '';
  const minutes = Math.round((new Date(eta).getTime() - now.getTime()) / 60000);
  if (Number.isNaN(minutes)) return '';
  if (minutes <= 0) return 'due now';
  return minutes < 60 ? `in ${minutes} min` : `in ${Math.floor(minutes / 60)} h ${minutes % 60} min`;
}

/** "just now", "3 min ago": how old the partner's last position is. */
export function seenLabel(updatedAt: string | null | undefined, now: Date): string {
  if (!updatedAt) return '';
  const seconds = Math.round((now.getTime() - new Date(updatedAt).getTime()) / 1000);
  if (Number.isNaN(seconds)) return '';
  if (seconds < 45) return 'just now';
  const minutes = Math.round(seconds / 60);
  return minutes < 60 ? `${minutes} min ago` : `${Math.floor(minutes / 60)} h ago`;
}

export type Marker = { key: string; x: number; y: number };

/**
 * Place points on a `width` x `height` canvas, keeping the proportions (a degree of longitude is
 * shorter than one of latitude). The north is up. One point sits in the middle.
 * TODO(M1): the app has no map library yet (the browse and land screens draw a simple canvas too);
 * swap this for react-native-maps when one is added.
 */
export function project(
  points: { key: string; at: LatLng }[],
  width: number,
  height: number,
  padding = 28,
): Marker[] {
  if (points.length === 0) return [];
  const midLat = points.reduce((sum, p) => sum + p.at.lat, 0) / points.length;
  const kx = Math.cos((midLat * Math.PI) / 180);
  const xs = points.map((p) => p.at.lng * kx);
  const ys = points.map((p) => p.at.lat);
  const minX = Math.min(...xs);
  const maxX = Math.max(...xs);
  const minY = Math.min(...ys);
  const maxY = Math.max(...ys);
  const spanX = maxX - minX;
  const spanY = maxY - minY;
  const innerW = width - 2 * padding;
  const innerH = height - 2 * padding;
  const scale = spanX === 0 && spanY === 0 ? 0 : Math.min(
    spanX === 0 ? Infinity : innerW / spanX,
    spanY === 0 ? Infinity : innerH / spanY,
  );
  return points.map((p, i) => ({
    key: p.key,
    x: width / 2 + ((xs[i] ?? 0) - (minX + maxX) / 2) * scale,
    y: height / 2 - ((ys[i] ?? 0) - (minY + maxY) / 2) * scale,
  }));
}
