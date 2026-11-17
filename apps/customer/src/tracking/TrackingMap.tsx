import { StyleSheet, View } from 'react-native';
import { Text } from '@farm/ui';
import { project, type LatLng } from './tracking.ts';

const WIDTH = 320;
const HEIGHT = 220;

type Props = { partner: LatLng | null; drop: LatLng | null; pickup?: LatLng | null };

/** A plain canvas, like the browse and land screens: no map library in the app yet. */
export function TrackingMap({ partner, drop, pickup }: Props) {
  const points = [
    ...(pickup ? [{ key: 'pickup', at: pickup }] : []),
    ...(drop ? [{ key: 'drop', at: drop }] : []),
    ...(partner ? [{ key: 'partner', at: partner }] : []),
  ];
  const markers = project(points, WIDTH, HEIGHT);
  const glyph: Record<string, string> = { pickup: '🌾', drop: '🏠', partner: '🛵' };
  return (
    <View style={styles.canvas}>
      {markers.map((m) => (
        <View key={m.key} style={[styles.marker, { left: m.x - 16, top: m.y - 16 }]}>
          <Text style={styles.glyph}>{glyph[m.key]}</Text>
        </View>
      ))}
      {markers.length === 0 && <Text variant="caption">The map appears once a partner is on the way.</Text>}
    </View>
  );
}

const styles = StyleSheet.create({
  canvas: {
    width: WIDTH,
    height: HEIGHT,
    alignSelf: 'center',
    backgroundColor: '#e8f1ea',
    borderRadius: 12,
    borderWidth: 1,
    borderColor: '#cfe0d4',
    alignItems: 'center',
    justifyContent: 'center',
  },
  marker: { position: 'absolute', width: 32, height: 32, alignItems: 'center', justifyContent: 'center' },
  glyph: { fontSize: 24 },
});
