import { ActivityIndicator, Linking, ScrollView, StyleSheet, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { useQueryClient } from '@tanstack/react-query';
import { Button, Card, Text } from '@farm/ui';
import { RatingForm, TrackingMap, useTracking } from '../src/tracking/index.ts';
import {
  PHASE_TEXT, canRate, distanceLabel, etaLabel, hasPartnerPosition, haversineKm, seenLabel,
} from '../src/tracking/tracking.ts';

/** Issue #8: where the delivery partner is, and a rating once the order is COMPLETED. */
export default function OrderTrackingScreen() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const { id } = useLocalSearchParams<{ id: string }>();
  const { order, track, phase } = useTracking(id ?? '');

  if (order.isLoading) {
    return (
      <View style={styles.center}>
        <ActivityIndicator size="large" color="#2d6a4f" />
      </View>
    );
  }
  if (!order.data || !phase) {
    return (
      <View style={styles.center}>
        <Text variant="h2">Order not found</Text>
        <Button label="Back" variant="secondary" onPress={() => router.back()} />
      </View>
    );
  }

  const now = new Date();
  const detail = order.data;
  const trackData = track.data ?? null;
  const drop = detail.deliveryAddress
    ? { lat: detail.deliveryAddress.location.coordinates[1], lng: detail.deliveryAddress.location.coordinates[0] }
    : null;
  const pickupPoint = detail.items[0]?.pickupLocation.coordinates;
  const pickup = pickupPoint ? { lat: pickupPoint[1], lng: pickupPoint[0] } : null;
  const partner = hasPartnerPosition(trackData)
    ? { lat: trackData.partnerLat, lng: trackData.partnerLng }
    : null;
  const live = phase === 'IN_TRANSIT' || phase === 'POOLED';
  const eta = etaLabel(trackData?.eta, now);
  const seen = seenLabel(trackData?.partnerUpdatedAt, now);

  return (
    <ScrollView style={styles.screen} contentContainerStyle={styles.content}>
      <Text variant="h1">Track delivery</Text>
      <Text variant="label">{PHASE_TEXT[phase]}</Text>

      {live && (
        <Card style={styles.card}>
          <TrackingMap partner={partner} drop={drop} pickup={phase === 'POOLED' ? pickup : null} />
          {partner && drop && (
            <Text variant="caption">
              {distanceLabel(haversineKm(partner, drop))} from you{eta ? ` · arriving ${eta}` : ''}
              {seen ? ` · position updated ${seen}` : ''}
            </Text>
          )}
          {!partner && <Text variant="caption">Waiting for the partner&apos;s first position.</Text>}
          {trackData?.partnerName && (
            <Text variant="label">
              {trackData.partnerName}
              {trackData.partnerPhone ? ` · ${trackData.partnerPhone}` : ''}
            </Text>
          )}
          {trackData?.partnerPhone && (
            <Button
              label="Call the partner"
              variant="secondary"
              onPress={() => Linking.openURL(`tel:${trackData.partnerPhone}`)}
            />
          )}
        </Card>
      )}

      <Card style={styles.card}>
        <Text variant="label">Order {detail.id.slice(0, 8).toUpperCase()}</Text>
        {detail.events.map((event, index) => (
          <Text key={`${event.status}-${index}`} variant="caption">
            {event.status.replace(/_/g, ' ').toLowerCase()} ·{' '}
            {new Date(event.occurredAt).toLocaleString('en-IN', {
              day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit',
            })}
          </Text>
        ))}
      </Card>

      {detail.rating && (
        <Card style={styles.card}>
          <Text variant="label">Your rating: {'★'.repeat(detail.rating.score)}</Text>
          {detail.rating.comment && <Text variant="caption">{detail.rating.comment}</Text>}
        </Card>
      )}
      {canRate(detail.status, detail.rating != null) && (
        <RatingForm
          orderId={detail.id}
          onRated={() => queryClient.invalidateQueries({ queryKey: ['order', detail.id] })}
        />
      )}

      <Button label="Back to orders" variant="secondary" onPress={() => router.replace('/orders')} />
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#f5f5f0' },
  content: { padding: 16, gap: 12 },
  card: { gap: 8 },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: 12, backgroundColor: '#f5f5f0' },
});
