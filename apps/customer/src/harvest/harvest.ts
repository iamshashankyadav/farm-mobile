/** Pure helpers for the harvest screen (no React, so they are unit tested). */

export type PaymentLike = { kind: string; amount: number; status: string };

/** The part of the order service's harvest order the screen needs. */
export type HarvestOrderLike = {
  id: string;
  status: string;
  harvestOption?: string | null;
  subtotal: number;
  deliveryFee: number;
  total: number;
  balanceDue?: number;
  payments?: PaymentLike[];
};

/**
 * Where a reservation's harvest is:
 *  NOT_READY   no harvest order yet (the quality check has not passed)
 *  CHOOSE      the harvest passed; the customer has to pick DELIVERY or BUYBACK
 *  DELIVERY    delivery chosen, on its way (or waiting for the balance)
 *  COMPLETED   delivered and paid, or bought back and credited
 *  CANCELLED
 */
export type Stage = 'NOT_READY' | 'CHOOSE' | 'DELIVERY' | 'COMPLETED' | 'CANCELLED';

export function stageOf(order: HarvestOrderLike | null): Stage {
  if (!order) return 'NOT_READY';
  if (order.status === 'CANCELLED') return 'CANCELLED';
  if (order.status === 'COMPLETED') return 'COMPLETED';
  return order.harvestOption ? 'DELIVERY' : 'CHOOSE';
}

/** Poll until the order is closed. */
export function shouldPollHarvest(stage: Stage): boolean {
  return stage !== 'COMPLETED' && stage !== 'CANCELLED';
}

function sum(payments: PaymentLike[], kind: string): number {
  return payments
    .filter((p) => p.kind === kind && p.status === 'SUCCEEDED')
    .reduce((total, p) => total + p.amount, 0);
}

export type Summary = {
  value: number; // what the harvest is worth
  deliveryFee: number;
  depositPaid: number;
  balancePaid: number;
  balanceDue: number; // still to pay on delivery
  credit: number; // buy-back credit and refunds paid to the customer
};

export function summarise(order: HarvestOrderLike): Summary {
  const payments = order.payments ?? [];
  return {
    value: order.subtotal,
    deliveryFee: order.deliveryFee,
    depositPaid: sum(payments, 'DEPOSIT'),
    balancePaid: sum(payments, 'BALANCE'),
    balanceDue: order.balanceDue ?? 0,
    credit: sum(payments, 'REFUND'),
  };
}

export const STAGE_TEXT: Record<Stage, string> = {
  NOT_READY: 'Your harvest is being checked by an inspector. You can choose here once it passes.',
  CHOOSE: 'Your harvest passed the quality check. Choose what happens next.',
  DELIVERY: 'Your harvest is on its way to you.',
  COMPLETED: 'All done.',
  CANCELLED: 'This harvest order was cancelled and your deposit refunded.',
};

/** Delivery dates the customer can pick: tomorrow and the next two days (the backend allows 30). */
export function deliveryDates(today: Date, count = 3): string[] {
  return Array.from({ length: count }, (_, i) => {
    const day = new Date(Date.UTC(today.getFullYear(), today.getMonth(), today.getDate() + i + 1));
    return day.toISOString().slice(0, 10);
  });
}

export type ChoiceError = { message: string; refresh: boolean };

const CHOICE_ERRORS: Record<string, ChoiceError> = {
  HARVEST_NOT_READY: { message: 'The harvest has not passed the quality check yet.', refresh: true },
  HARVEST_OPTION_CHOSEN: { message: 'You already chose an option for this harvest.', refresh: true },
  HARVEST_ORDER_CLOSED: { message: 'This harvest order is closed.', refresh: true },
  RESERVATION_NOT_FOUND: { message: 'Reservation not found.', refresh: false },
  INVALID_DELIVERY_DATE: { message: 'Pick a delivery date from today up to 30 days ahead.', refresh: false },
};

/** Map the order service's error body to a message for the customer. */
export function parseChoiceError(status: number, body: unknown): ChoiceError {
  const code = (body as { error?: { code?: string } } | null | undefined)?.error?.code ?? '';
  const known = CHOICE_ERRORS[code];
  if (known) return known;
  if (status === 401 || status === 403) return { message: 'Log in again to continue.', refresh: false };
  if (status === 422) return { message: 'Check the delivery address and try again.', refresh: false };
  return { message: 'Something went wrong. Try again.', refresh: false };
}
