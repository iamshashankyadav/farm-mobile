import { View, StyleSheet } from 'react-native';
import { Button, Text, Card } from '@farm/ui';

export default function HomeScreen() {
  return (
    <View style={styles.container}>
      <Card style={styles.card}>
        <Text variant="h1">🚚 Farm Delivery</Text>
        <Text variant="caption" style={{ marginTop: 8 }}>
          Sprint 1 scaffold – delivery app
        </Text>
        <Button label="Get Started" style={{ marginTop: 20 }} onPress={() => {}} />
      </Card>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f5f0',
    alignItems: 'center',
    justifyContent: 'center',
    padding: 24,
  },
  card: { width: '100%', maxWidth: 360 },
});
