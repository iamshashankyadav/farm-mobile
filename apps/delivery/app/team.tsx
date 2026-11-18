import React from 'react';
import { useRouter } from 'expo-router';
import { TeamScreen } from '@farm/ui';

export default function DeliveryTeamRoute() {
  const router = useRouter();
  return <TeamScreen onBack={() => router.back()} title="Our Team" />;
}
