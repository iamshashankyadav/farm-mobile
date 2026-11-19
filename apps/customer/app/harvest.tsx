import { useState } from 'react';
import { ActivityIndicator, ScrollView, StyleSheet, TextInput, TouchableOpacity, View } from 'react-native';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { useQueryClient } from '@tanstack/react-query';
import { Button, Card, Text } from '@farm/ui';
import { chooseOption, useHarvest } from '../src/harvest/index.ts';
import { STAGE_TEXT, deliveryDates, summarise, type Summary } from '../src/harvest/harvest.ts';
import { emptyAddress, validateAddress, type AddressErrors, type AddressForm } from '../src/checkout/address.ts';
import { formatRupees } from '../src/checkout/format.ts';

type Option = 'DELIVERY' | 'BUYBACK';

/** Issue #10: after the quality check, choose delivery or buy-back and follow the harvest order. */
export default function HarvestScreen() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const { reservationId } = useLocalSearchParams<{ reservationId: string }>();
  const harvest = useHarvest(reservationId ?? '');
  const [option, setOption] = useState<Option | null>(null);
  const [form, setForm] = useState<AddressForm>(emptyAddress);
  const [errors, setErrors] = useState<AddressErrors>({});
  const dates = deliveryDates(new Date());
  const [date, setDate] = useState<string>(dates[0] ?? '');
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  if (harvest.isLoading) {
    return (
      <View style={styles.center}>
        <ActivityIndicator size="large" color="#2d6a4f" />
      </View>
    );
  }
  const order = harvest.data ?? null;
  const stage = harvest.stage;
  const summary = order ? summarise(order) : null;

  async function confirm() {
    if (!option || !reservationId) return;
    let deliveryAddress;
    if (option === 'DELIVERY') {
      const result = validateAddress(form);
      if (!result.ok) return setErrors(result.errors);
      setErrors({});
      deliveryAddress = result.value;
    }
    setBusy(true);
    setMessage(null);
    const result = await chooseOption({
      reservationId,
      option,
      deliveryAddress: deliveryAddress ?? null,
      deliveryDate: option === 'DELIVERY' ? date : null,
    });
    setBusy(false);
    if (result.ok) {
      queryClient.setQueryData(['harvest', reservationId], result.order);
      return;
    }
    setMessage(result.error.message);
    if (result.error.refresh) await harvest.refetch();
  }

  const field = (key: keyof AddressForm, label: string) => (
    <View key={key}>
      <TextInput
        style={[styles.input, errors[key] ? styles.inputError : null]}
        placeholder={label}
        value={form[key]}
        onChangeText={(text) => setForm({ ...form, [key]: text })}
        autoCapitalize="none"
      />
      {errors[key] && <Text style={styles.error}>{errors[key]}</Text>}
    </View>
  );

  return (
    <ScrollView style={styles.screen} contentContainerStyle={styles.content}>
      <Text variant="h1">Your harvest</Text>
      <Text variant="caption">{STAGE_TEXT[stage]}</Text>

      {order && summary && <Prices summary={summary} showFee={order.harvestOption === 'DELIVERY'} />}

      {stage === 'CHOOSE' && (
        <>
          <OptionCard
            selected={option === 'DELIVERY'}
            title="🚚  Deliver it to me"
            text="Pooled doorstep delivery. The balance is paid when it arrives."
            onPress={() => setOption('DELIVERY')}
          />
          <OptionCard
            selected={option === 'BUYBACK'}
            title="💰  Buy-back"
            text="The farmer buys your harvest and credits you. Nothing is delivered."
            onPress={() => setOption('BUYBACK')}
          />
          {option === 'DELIVERY' && (
            <Card style={styles.card}>
              <Text variant="label">Delivery address</Text>
              {field('line1', 'Street address')}
              {field('line2', 'Area (optional)')}
              {field('city', 'City')}
              {field('pincode', 'Pincode')}
              {field('contactPhone', 'Phone (optional)')}
              {/* TODO(M1): use the device location here, as in checkout */}
              <Text variant="label">Delivery date</Text>
              <View style={styles.dates}>
                {dates.map((d) => (
                  <TouchableOpacity key={d} onPress={() => setDate(d)} style={[styles.chip, d === date && styles.chipOn]}>
                    <Text style={d === date ? styles.chipTextOn : undefined}>{d.slice(5)}</Text>
                  </TouchableOpacity>
                ))}
              </View>
            </Card>
          )}
          {message && <Text style={styles.error}>{message}</Text>}
          <Button
            label={busy ? 'Saving…' : option === 'BUYBACK' ? 'Sell it back' : 'Confirm delivery'}
            disabled={!option || busy}
            onPress={confirm}
          />
        </>
      )}

      {order && (stage === 'DELIVERY' || (stage === 'COMPLETED' && order.harvestOption === 'DELIVERY')) && (
        <Button
          label="Track delivery"
          onPress={() => router.push({ pathname: '/order-tracking', params: { id: order.id } })}
        />
      )}
      {stage === 'COMPLETED' && summary && summary.credit > 0 && order?.harvestOption === 'BUYBACK' && (
        <Card style={styles.card}>
          <Text variant="h2">Credited {formatRupees(summary.credit)}</Text>
          <Text variant="caption">The farmer bought your harvest back.</Text>
        </Card>
      )}
      {stage === 'NOT_READY' && <Text variant="caption">This page refreshes by itself.</Text>}
      <Button label="Back" variant="secondary" onPress={() => router.back()} />
    </ScrollView>
  );
}

function Prices({ summary, showFee }: { summary: Summary; showFee: boolean }) {
  return (
    <Card style={styles.card}>
      <Row label="Harvest value" value={formatRupees(summary.value)} />
      {showFee && <Row label="Delivery fee" value={formatRupees(summary.deliveryFee)} />}
      <Row label="Deposit paid" value={formatRupees(summary.depositPaid)} />
      {summary.balancePaid > 0 && <Row label="Balance paid" value={formatRupees(summary.balancePaid)} />}
      {summary.balanceDue > 0 && <Row label="Balance due on delivery" value={formatRupees(summary.balanceDue)} bold />}
    </Card>
  );
}

function Row({ label, value, bold }: { label: string; value: string; bold?: boolean }) {
  return (
    <View style={styles.row}>
      <Text variant={bold ? 'label' : 'caption'}>{label}</Text>
      <Text variant={bold ? 'label' : 'caption'}>{value}</Text>
    </View>
  );
}

function OptionCard(props: { selected: boolean; title: string; text: string; onPress: () => void }) {
  return (
    <TouchableOpacity onPress={props.onPress} accessibilityRole="radio" accessibilityState={{ selected: props.selected }}>
      <Card style={props.selected ? { ...styles.card, ...styles.selected } : styles.card}>
        <Text variant="label">{props.title}</Text>
        <Text variant="caption">{props.text}</Text>
      </Card>
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#f5f5f0' },
  content: { padding: 16, gap: 12 },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: '#f5f5f0' },
  card: { gap: 8 },
  selected: { borderWidth: 2, borderColor: '#2d6a4f' },
  row: { flexDirection: 'row', justifyContent: 'space-between' },
  input: { borderWidth: 1, borderColor: '#ddd', borderRadius: 8, padding: 10, backgroundColor: '#fff' },
  inputError: { borderColor: '#c62828' },
  error: { color: '#c62828' },
  dates: { flexDirection: 'row', gap: 8 },
  chip: { paddingVertical: 8, paddingHorizontal: 14, borderRadius: 16, borderWidth: 1, borderColor: '#ccc', backgroundColor: '#fff' },
  chipOn: { backgroundColor: '#2d6a4f', borderColor: '#2d6a4f' },
  chipTextOn: { color: '#fff' },
});
