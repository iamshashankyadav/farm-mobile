/**
 * Issue #6 – Delivery app: today's route (map + ordered stops)
 *
 * Shows an ordered stop list: pickups first, then drops.
 * Tapping "Navigate" on the next pending stop deep-links to Google Maps.
 * A mock map thumbnail sits at the top (replaced with react-native-maps
 * when the native module is wired up in a later sprint).
 */

import React, { useState, useCallback } from 'react';
import {
  View,
  ScrollView,
  StyleSheet,
  Linking,
  TouchableOpacity,
  ActivityIndicator,
  Alert,
} from 'react-native';
import { router } from 'expo-router';
import { Button, Text, Card } from '@farm/ui';
import { useAuth } from '@farm/api';

// ─── Types ───────────────────────────────────────────────────────────────────

type StopType = 'pickup' | 'drop';
type StopStatus = 'pending' | 'done' | 'skipped';

interface RouteStop {
  id: string;
  seq: number;          // 1-based display order
  type: StopType;
  status: StopStatus;
  label: string;        // "Farm: Greenfield Organics"
  address: string;
  lat: number;
  lng: number;
  customerName: string;
  items: string[];      // e.g. ["2 × Tomatoes (1 kg)", "1 × Spinach (500 g)"]
  otp?: string;         // present for drops (contactless proof)
}

// ─── Mock data (replaced by API in Sprint 3) ─────────────────────────────────

const MOCK_STOPS: RouteStop[] = [
  {
    id: 'stop-1',
    seq: 1,
    type: 'pickup',
    status: 'done',
    label: 'Farm: Greenfield Organics',
    address: 'Sector 12, Palam Vihar, Gurugram',
    lat: 28.5021,
    lng: 76.9986,
    customerName: 'Greenfield Organics (Farm)',
    items: ['2 × Tomatoes (1 kg)', '1 × Spinach (500 g)', '3 × Carrots (500 g)'],
  },
  {
    id: 'stop-2',
    seq: 2,
    type: 'pickup',
    status: 'pending',
    label: 'Farm: Sunrise Agro',
    address: 'Village Dhankot, Gurugram',
    lat: 28.4595,
    lng: 76.9558,
    customerName: 'Sunrise Agro (Farm)',
    items: ['4 × Potatoes (1 kg)', '2 × Onions (1 kg)'],
  },
  {
    id: 'stop-3',
    seq: 3,
    type: 'drop',
    status: 'pending',
    label: 'Customer: Priya Sharma',
    address: 'Flat 304, Orchid Heights, Sector 56, Gurugram',
    lat: 28.4231,
    lng: 77.0421,
    customerName: 'Priya Sharma',
    items: ['2 × Tomatoes (1 kg)', '4 × Potatoes (1 kg)'],
    otp: '7823',
  },
  {
    id: 'stop-4',
    seq: 4,
    type: 'drop',
    status: 'pending',
    label: 'Customer: Rahul Mehta',
    address: 'B-12, DLF Phase 2, Gurugram',
    lat: 28.4751,
    lng: 77.0901,
    customerName: 'Rahul Mehta',
    items: ['1 × Spinach (500 g)', '3 × Carrots (500 g)', '2 × Onions (1 kg)'],
    otp: '4591',
  },
];

// ─── Helpers ─────────────────────────────────────────────────────────────────

function mapsUrl(lat: number, lng: number, label: string): string {
  const encoded = encodeURIComponent(label);
  return `https://www.google.com/maps/dir/?api=1&destination=${lat},${lng}&destination_place_id=${encoded}&travelmode=driving`;
}

function stopTypeColor(type: StopType): string {
  return type === 'pickup' ? '#1b5e20' : '#1565c0';
}

function stopTypeBg(type: StopType): string {
  return type === 'pickup' ? '#e8f5e9' : '#e3f2fd';
}

function statusIcon(status: StopStatus): string {
  if (status === 'done') return '✅';
  if (status === 'skipped') return '⏭️';
  return '⏳';
}

// ─── Sub-components ───────────────────────────────────────────────────────────

interface StopCardProps {
  stop: RouteStop;
  isNext: boolean;
  onNavigate: (stop: RouteStop) => void;
  onMarkDone: (stopId: string) => void;
}

function StopCard({ stop, isNext, onNavigate, onMarkDone }: StopCardProps) {
  const isDone = stop.status === 'done';
  const color = stopTypeColor(stop.type);
  const bg = stopTypeBg(stop.type);

  return (
    <Card style={{ ...styles.stopCard, ...(isDone ? styles.stopCardDone : {}) }}>
      {/* Header row */}
      <View style={styles.stopHeader}>
        <View style={[styles.seqBadge, { backgroundColor: isDone ? '#ccc' : color }]}>
          <Text style={styles.seqText}>{stop.seq}</Text>
        </View>
        <View style={{ flex: 1, marginLeft: 10 }}>
          <View style={styles.typePill}>
            <View style={[styles.typeDot, { backgroundColor: bg, borderColor: color }]}>
              <Text style={[styles.typeLabel, { color }]}>
                {stop.type === 'pickup' ? '📦 PICKUP' : '🚪 DROP'}
              </Text>
            </View>
            {isNext && (
              <View style={styles.nextBadge}>
                <Text style={styles.nextBadgeText}>NEXT</Text>
              </View>
            )}
            <Text style={styles.statusIcon}>{statusIcon(stop.status)}</Text>
          </View>
          <Text variant="h2" style={[styles.stopLabel, isDone && styles.textDone]}>
            {stop.label}
          </Text>
        </View>
      </View>

      {/* Address */}
      <Text variant="caption" style={styles.address}>
        📍 {stop.address}
      </Text>

      {/* Items */}
      <View style={styles.itemsBox}>
        <Text variant="label">Items</Text>
        {stop.items.map((item, i) => (
          <Text key={i} variant="caption" style={styles.itemRow}>
            • {item}
          </Text>
        ))}
      </View>

      {/* OTP hint for drops */}
      {stop.type === 'drop' && stop.otp && !isDone && (
        <View style={styles.otpBox}>
          <Text variant="label" style={{ color: '#7b1fa2' }}>
            Delivery OTP
          </Text>
          <Text style={styles.otpValue}>{stop.otp}</Text>
          <Text variant="caption" style={{ color: '#7b1fa2', marginTop: 2 }}>
            Share this with the customer to confirm receipt
          </Text>
        </View>
      )}

      {/* Action buttons */}
      {!isDone && (
        <View style={styles.stopActions}>
          <Button
            label="🗺️  Navigate"
            variant="secondary"
            style={styles.navBtn}
            onPress={() => onNavigate(stop)}
          />
          <Button
            label={stop.type === 'pickup' ? '✅ Picked Up' : '✅ Delivered'}
            style={styles.doneBtn}
            onPress={() => onMarkDone(stop.id)}
          />
        </View>
      )}
    </Card>
  );
}

// ─── Mock Map Banner ─────────────────────────────────────────────────────────

function MapBanner({ totalStops, doneStops }: { totalStops: number; doneStops: number }) {
  const pct = totalStops > 0 ? Math.round((doneStops / totalStops) * 100) : 0;
  return (
    <View style={styles.mapBanner}>
      <View style={styles.mapPlaceholder}>
        <Text style={styles.mapEmoji}>🗺️</Text>
        <Text variant="caption" style={{ color: '#fff', marginTop: 4 }}>
          Map view (react-native-maps — Sprint 3)
        </Text>
      </View>
      {/* Progress bar */}
      <View style={styles.progressOuter}>
        <View style={[styles.progressInner, { width: `${pct}%` as `${number}%` }]} />
      </View>
      <Text variant="caption" style={styles.progressLabel}>
        {doneStops} / {totalStops} stops completed ({pct}%)
      </Text>
    </View>
  );
}

// ─── Main Screen ─────────────────────────────────────────────────────────────

export default function TodaysRouteScreen() {
  const { isAuthenticated, user, isLoading } = useAuth();
  const [stops, setStops] = useState<RouteStop[]>(MOCK_STOPS);
  const [navigating, setNavigating] = useState<string | null>(null);

  // Guard: must be authenticated delivery partner
  if (isLoading) {
    return (
      <View style={styles.center}>
        <ActivityIndicator size="large" color="#2d6a4f" />
      </View>
    );
  }

  if (!isAuthenticated || !user?.roles?.includes('delivery')) {
    return (
      <View style={styles.center}>
        <Text variant="body">Access denied. Please log in as a delivery partner.</Text>
        <Button
          label="Go Back"
          variant="secondary"
          style={{ marginTop: 16 }}
          onPress={() => router.replace('/')}
        />
      </View>
    );
  }

  // Derived state
  const doneCount = stops.filter(s => s.status === 'done').length;
  const nextStop = stops.find(s => s.status === 'pending');

  const handleNavigate = useCallback(async (stop: RouteStop) => {
    const url = mapsUrl(stop.lat, stop.lng, stop.label);
    setNavigating(stop.id);
    try {
      const supported = await Linking.canOpenURL(url);
      if (supported) {
        await Linking.openURL(url);
      } else {
        Alert.alert('Maps not available', 'Could not open Google Maps on this device.');
      }
    } finally {
      setNavigating(null);
    }
  }, []);

  const handleMarkDone = useCallback((stopId: string) => {
    Alert.alert(
      'Confirm',
      nextStop?.type === 'pickup'
        ? 'Mark this pickup as collected?'
        : 'Confirm delivery with OTP / photo?',
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Confirm',
          onPress: () =>
            setStops(prev =>
              prev.map(s => (s.id === stopId ? { ...s, status: 'done' } : s)),
            ),
        },
      ],
    );
  }, [nextStop]);

  const allDone = doneCount === stops.length;

  return (
    <View style={styles.root}>
      {/* Header */}
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()} style={styles.backBtn}>
          <Text style={styles.backArrow}>←</Text>
        </TouchableOpacity>
        <Text variant="h2" style={styles.headerTitle}>
          Today's Route
        </Text>
        <View style={{ width: 36 }} />
      </View>

      <ScrollView contentContainerStyle={styles.scroll} showsVerticalScrollIndicator={false}>
        {/* Map banner */}
        <MapBanner totalStops={stops.length} doneStops={doneCount} />

        {/* Route summary */}
        <View style={styles.summaryRow}>
          <View style={styles.summaryChip}>
            <Text style={styles.summaryNum}>
              {stops.filter(s => s.type === 'pickup').length}
            </Text>
            <Text variant="caption">Pickups</Text>
          </View>
          <View style={styles.summaryChip}>
            <Text style={styles.summaryNum}>
              {stops.filter(s => s.type === 'drop').length}
            </Text>
            <Text variant="caption">Drops</Text>
          </View>
          <View style={styles.summaryChip}>
            <Text style={[styles.summaryNum, { color: '#2d6a4f' }]}>{doneCount}</Text>
            <Text variant="caption">Done</Text>
          </View>
        </View>

        {/* All-done banner */}
        {allDone && (
          <Card style={styles.allDoneCard}>
            <Text style={styles.allDoneText}>🎉 Route complete! Great work today.</Text>
          </Card>
        )}

        {/* Stop list */}
        {stops.map(stop => (
          <StopCard
            key={stop.id}
            stop={stop}
            isNext={nextStop?.id === stop.id}
            onNavigate={handleNavigate}
            onMarkDone={handleMarkDone}
          />
        ))}

        <View style={{ height: 32 }} />
      </ScrollView>
    </View>
  );
}

// ─── Styles ───────────────────────────────────────────────────────────────────

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#f5f5f0' },
  center: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 24,
    backgroundColor: '#f5f5f0',
  },

  // Header
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#2d6a4f',
    paddingTop: 52,
    paddingBottom: 14,
    paddingHorizontal: 16,
    justifyContent: 'space-between',
  },
  headerTitle: { color: '#fff', fontSize: 18 },
  backBtn: { width: 36, alignItems: 'flex-start' },
  backArrow: { color: '#fff', fontSize: 22 },

  // Scroll
  scroll: { padding: 16, paddingTop: 12 },

  // Map banner
  mapBanner: {
    borderRadius: 14,
    overflow: 'hidden',
    marginBottom: 14,
  },
  mapPlaceholder: {
    backgroundColor: '#1b5e20',
    height: 140,
    alignItems: 'center',
    justifyContent: 'center',
  },
  mapEmoji: { fontSize: 44 },

  // Progress
  progressOuter: {
    height: 6,
    backgroundColor: '#c8e6c9',
  },
  progressInner: {
    height: 6,
    backgroundColor: '#2d6a4f',
  },
  progressLabel: {
    textAlign: 'center',
    marginTop: 6,
    marginBottom: 2,
    color: '#555',
  },

  // Summary chips
  summaryRow: {
    flexDirection: 'row',
    gap: 10,
    marginBottom: 14,
  },
  summaryChip: {
    flex: 1,
    backgroundColor: '#fff',
    borderRadius: 10,
    padding: 10,
    alignItems: 'center',
    shadowColor: '#000',
    shadowOpacity: 0.05,
    shadowRadius: 4,
    elevation: 2,
  },
  summaryNum: { fontSize: 22, fontWeight: '700', color: '#1b1b1b' },

  // All-done
  allDoneCard: {
    backgroundColor: '#e8f5e9',
    marginBottom: 14,
    alignItems: 'center',
  },
  allDoneText: { fontSize: 15, fontWeight: '600', color: '#2d6a4f' },

  // Stop card
  stopCard: {
    marginBottom: 12,
  },
  stopCardDone: {
    opacity: 0.6,
  },
  stopHeader: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    marginBottom: 8,
  },
  seqBadge: {
    width: 30,
    height: 30,
    borderRadius: 15,
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: 2,
  },
  seqText: { color: '#fff', fontWeight: '700', fontSize: 14 },
  typePill: { flexDirection: 'row', alignItems: 'center', gap: 6, marginBottom: 3 },
  typeDot: {
    borderWidth: 1,
    borderRadius: 6,
    paddingHorizontal: 7,
    paddingVertical: 2,
  },
  typeLabel: { fontSize: 10, fontWeight: '700' },
  nextBadge: {
    backgroundColor: '#ff6f00',
    borderRadius: 6,
    paddingHorizontal: 7,
    paddingVertical: 2,
  },
  nextBadgeText: { color: '#fff', fontSize: 10, fontWeight: '700' },
  statusIcon: { fontSize: 16, marginLeft: 'auto' },
  stopLabel: { fontSize: 15, fontWeight: '600', color: '#1b1b1b' },
  textDone: { textDecorationLine: 'line-through', color: '#888' },
  address: { color: '#555', marginBottom: 8 },

  // Items
  itemsBox: {
    backgroundColor: '#fafafa',
    borderRadius: 8,
    padding: 10,
    marginBottom: 10,
    borderWidth: 1,
    borderColor: '#eee',
  },
  itemRow: { marginTop: 2, color: '#444' },

  // OTP
  otpBox: {
    backgroundColor: '#f3e5f5',
    borderRadius: 8,
    padding: 10,
    marginBottom: 10,
    borderWidth: 1,
    borderColor: '#ce93d8',
  },
  otpValue: {
    fontSize: 28,
    fontWeight: '700',
    color: '#6a1b9a',
    letterSpacing: 6,
    marginTop: 4,
  },

  // Stop actions
  stopActions: {
    flexDirection: 'row',
    gap: 10,
    marginTop: 4,
  },
  navBtn: { flex: 1 },
  doneBtn: { flex: 1 },
});
