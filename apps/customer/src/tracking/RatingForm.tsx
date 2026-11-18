import { useState } from 'react';
import { StyleSheet, TextInput, TouchableOpacity, View } from 'react-native';
import { Button, Card, Text } from '@farm/ui';
import { rateOrder } from './api.ts';

type Props = { orderId: string; onRated: () => void };

export function RatingForm({ orderId, onRated }: Props) {
  const [score, setScore] = useState(0);
  const [comment, setComment] = useState('');
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  async function submit() {
    if (score === 0) return setMessage('Pick 1 to 5 stars.');
    setBusy(true);
    setMessage(null);
    const result = await rateOrder(orderId, score, comment);
    setBusy(false);
    if (result.ok) return onRated();
    setMessage(result.message);
    // already rated elsewhere: refresh so the form turns into the saved rating
    if (result.message.startsWith('You already')) onRated();
  }

  return (
    <Card style={styles.card}>
      <Text variant="h2">How was your delivery?</Text>
      <View style={styles.stars}>
        {[1, 2, 3, 4, 5].map((n) => (
          <TouchableOpacity key={n} onPress={() => setScore(n)} accessibilityLabel={`${n} stars`}>
            <Text style={[styles.star, n <= score && styles.starOn]}>★</Text>
          </TouchableOpacity>
        ))}
      </View>
      <TextInput
        style={styles.input}
        placeholder="Add a comment (optional)"
        value={comment}
        onChangeText={setComment}
        maxLength={500}
        multiline
      />
      {message && <Text style={styles.error}>{message}</Text>}
      <Button label={busy ? 'Saving…' : 'Submit rating'} disabled={busy} onPress={submit} />
    </Card>
  );
}

const styles = StyleSheet.create({
  card: { gap: 10 },
  stars: { flexDirection: 'row', gap: 6 },
  star: { fontSize: 34, color: '#ccc' },
  starOn: { color: '#f2a900' },
  input: {
    borderWidth: 1,
    borderColor: '#ddd',
    borderRadius: 8,
    padding: 10,
    minHeight: 60,
    textAlignVertical: 'top',
    backgroundColor: '#fff',
  },
  error: { color: '#c62828' },
});
