import { useQuery } from '@tanstack/react-query';
import { findHarvestOrder } from './api.ts';
import { shouldPollHarvest, stageOf } from './harvest.ts';

const POLL_MS = 15_000;

export function useHarvest(reservationId: string) {
  const query = useQuery({
    queryKey: ['harvest', reservationId],
    queryFn: () => findHarvestOrder(reservationId),
    refetchInterval: (q) => (shouldPollHarvest(stageOf(q.state.data ?? null)) ? POLL_MS : false),
  });
  return { ...query, stage: stageOf(query.data ?? null) };
}
