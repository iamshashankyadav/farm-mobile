import React, { useState } from 'react';
import {
  View,
  ScrollView,
  StyleSheet,
  TouchableOpacity,
  Image,
  Linking,
  SafeAreaView,
  StatusBar,
} from 'react-native';
import { Text } from './Text';
import { Card } from './Card';
import { teamData } from './teamData';

export interface TeamMember {
  name: string;
  github: string;
  role: string;
  built: string;
}

export interface TeamSection {
  name: string;
  about: string;
  members: TeamMember[];
}

export interface GuideInfo {
  name: string;
  role: string;
  photo: string;
  thanks: string;
}

export interface TeamData {
  guide: GuideInfo;
  teams: TeamSection[];
}

interface TeamScreenProps {
  onBack?: () => void;
  title?: string;
}

export function TeamScreen({ onBack, title = 'Our Team' }: TeamScreenProps) {
  const [photoError, setPhotoError] = useState(false);
  const data: TeamData = teamData as TeamData;

  const handleOpenGithub = (handle: string) => {
    const url = `https://github.com/${handle}`;
    Linking.openURL(url).catch(() => {});
  };

  return (
    <SafeAreaView style={styles.root}>
      <StatusBar barStyle="light-content" backgroundColor="#2d6a4f" />
      {/* Top Header */}
      <View style={styles.header}>
        {onBack ? (
          <TouchableOpacity
            onPress={onBack}
            style={styles.backButton}
            accessibilityLabel="Go back"
            accessibilityRole="button"
          >
            <Text style={styles.backIcon}>←</Text>
          </TouchableOpacity>
        ) : (
          <View style={styles.placeholder} />
        )}
        <Text variant="h2" style={styles.headerTitle}>
          {title}
        </Text>
        <View style={styles.placeholder} />
      </View>

      <ScrollView contentContainerStyle={styles.scrollContent} showsVerticalScrollIndicator={false}>
        {/* Mentor Acknowledgement Card */}
        <Card style={styles.mentorCard}>
          <View style={styles.mentorHeader}>
            <View style={styles.mentorAvatarWrapper}>
              {!photoError && data.guide.photo ? (
                <Image
                  source={{ uri: data.guide.photo }}
                  onError={() => setPhotoError(true)}
                  style={styles.mentorAvatar}
                />
              ) : (
                <View style={styles.mentorFallbackAvatar}>
                  <Text style={styles.mentorAvatarEmoji}>🎓</Text>
                </View>
              )}
            </View>

            <View style={styles.mentorHeaderText}>
              <View style={styles.mentorBadge}>
                <Text style={styles.mentorBadgeText}>Project Guide & Mentor</Text>
              </View>
              <Text variant="h1" style={styles.mentorName}>
                {data.guide.name}
              </Text>
              <Text variant="caption" style={styles.mentorRole}>
                {data.guide.role}
              </Text>
            </View>
          </View>

          <View style={styles.divider} />

          <View style={styles.quoteBox}>
            <Text style={styles.quoteMark}>“</Text>
            <Text variant="body" style={styles.mentorThanks}>
              {data.guide.thanks}
            </Text>
          </View>
        </Card>

        {/* Teams List */}
        {data.teams.map((team, idx) => (
          <View key={team.name} style={styles.teamSection}>
            <View style={styles.teamHeaderRow}>
              <Text style={styles.teamIndexIcon}>{idx === 0 ? '📱' : '💻'}</Text>
              <View style={{ flex: 1 }}>
                <Text variant="h2" style={styles.teamTitle}>
                  {team.name}
                </Text>
                <Text variant="caption" style={styles.teamAbout}>
                  {team.about}
                </Text>
              </View>
            </View>

            <View style={styles.membersGrid}>
              {team.members.map((member) => (
                <Card key={member.github} style={styles.memberCard}>
                  <View style={styles.memberTop}>
                    <View style={styles.memberAvatar}>
                      <Text style={styles.memberAvatarText}>
                        {member.name.charAt(0).toUpperCase()}
                      </Text>
                    </View>
                    <View style={{ flex: 1, marginLeft: 10 }}>
                      <Text variant="label" style={styles.memberName}>
                        {member.name}
                      </Text>
                      <View
                        style={[
                          styles.rolePill,
                          member.role.toLowerCase().includes('lead')
                            ? styles.leadRolePill
                            : styles.memberRolePill,
                        ]}
                      >
                        <Text
                          style={[
                            styles.rolePillText,
                            member.role.toLowerCase().includes('lead')
                              ? styles.leadRoleText
                              : styles.memberRoleText,
                          ]}
                        >
                          {member.role}
                        </Text>
                      </View>
                    </View>
                  </View>

                  <Text variant="caption" style={styles.builtText}>
                    {member.built}
                  </Text>

                  <TouchableOpacity
                    style={styles.githubLink}
                    onPress={() => handleOpenGithub(member.github)}
                    accessibilityRole="link"
                  >
                    <Text style={styles.githubIcon}>🐙</Text>
                    <Text style={styles.githubText}>@{member.github}</Text>
                  </TouchableOpacity>
                </Card>
              ))}
            </View>
          </View>
        ))}

        {/* Footer Acknowledgement */}
        <View style={styles.footer}>
          <Text variant="caption" style={styles.footerText}>
            Farm Platform Prototype · Academic Group Project
          </Text>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
    backgroundColor: '#f5f5f0',
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: '#2d6a4f',
    paddingHorizontal: 16,
    paddingVertical: 14,
  },
  headerTitle: {
    color: '#ffffff',
    fontSize: 18,
    fontWeight: '700',
  },
  backButton: {
    width: 36,
    height: 36,
    borderRadius: 18,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(255,255,255,0.15)',
  },
  backIcon: {
    color: '#ffffff',
    fontSize: 20,
    fontWeight: 'bold',
  },
  placeholder: {
    width: 36,
  },
  scrollContent: {
    padding: 16,
    paddingBottom: 40,
  },
  mentorCard: {
    backgroundColor: '#ffffff',
    borderRadius: 16,
    padding: 18,
    borderLeftWidth: 5,
    borderLeftColor: '#2d6a4f',
    marginBottom: 24,
    shadowColor: '#000',
    shadowOpacity: 0.08,
    shadowRadius: 10,
    shadowOffset: { width: 0, height: 4 },
    elevation: 4,
  },
  mentorHeader: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  mentorAvatarWrapper: {
    marginRight: 14,
  },
  mentorAvatar: {
    width: 68,
    height: 68,
    borderRadius: 34,
  },
  mentorFallbackAvatar: {
    width: 68,
    height: 68,
    borderRadius: 34,
    backgroundColor: '#e8f5e9',
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 2,
    borderColor: '#2d6a4f',
  },
  mentorAvatarEmoji: {
    fontSize: 32,
  },
  mentorHeaderText: {
    flex: 1,
  },
  mentorBadge: {
    alignSelf: 'flex-start',
    backgroundColor: '#e8f5e9',
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 6,
    marginBottom: 4,
  },
  mentorBadgeText: {
    fontSize: 11,
    fontWeight: '700',
    color: '#2d6a4f',
    textTransform: 'uppercase',
    letterSpacing: 0.5,
  },
  mentorName: {
    fontSize: 22,
    fontWeight: '700',
    color: '#1b1b1b',
  },
  mentorRole: {
    fontSize: 13,
    color: '#555555',
    marginTop: 2,
  },
  divider: {
    height: 1,
    backgroundColor: '#eeeeee',
    marginVertical: 14,
  },
  quoteBox: {
    flexDirection: 'row',
    backgroundColor: '#f8faf8',
    borderRadius: 10,
    padding: 12,
    borderWidth: 1,
    borderColor: '#e8f0e8',
  },
  quoteMark: {
    fontSize: 28,
    color: '#2d6a4f',
    lineHeight: 32,
    marginRight: 6,
    fontWeight: '700',
  },
  mentorThanks: {
    flex: 1,
    color: '#2c3e50',
    fontSize: 14,
    lineHeight: 20,
    fontStyle: 'italic',
  },
  teamSection: {
    marginBottom: 24,
  },
  teamHeaderRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 12,
  },
  teamIndexIcon: {
    fontSize: 24,
    marginRight: 10,
  },
  teamTitle: {
    fontSize: 18,
    fontWeight: '700',
    color: '#1b1b1b',
  },
  teamAbout: {
    fontSize: 13,
    color: '#666666',
    marginTop: 2,
  },
  membersGrid: {
    gap: 12,
  },
  memberCard: {
    backgroundColor: '#ffffff',
    borderRadius: 12,
    padding: 14,
    borderWidth: 1,
    borderColor: '#e5e7eb',
  },
  memberTop: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 8,
  },
  memberAvatar: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: '#e8f5e9',
    alignItems: 'center',
    justifyContent: 'center',
  },
  memberAvatarText: {
    fontSize: 16,
    fontWeight: '700',
    color: '#2d6a4f',
  },
  memberName: {
    fontSize: 15,
    fontWeight: '600',
    color: '#1b1b1b',
  },
  rolePill: {
    alignSelf: 'flex-start',
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderRadius: 4,
    marginTop: 3,
  },
  leadRolePill: {
    backgroundColor: '#fef3c7',
  },
  leadRoleText: {
    color: '#92400e',
    fontWeight: '700',
    fontSize: 11,
  },
  memberRolePill: {
    backgroundColor: '#f3f4f6',
  },
  memberRoleText: {
    color: '#4b5563',
    fontWeight: '600',
    fontSize: 11,
  },
  rolePillText: {
    fontSize: 11,
  },
  builtText: {
    color: '#444444',
    fontSize: 13,
    lineHeight: 18,
    marginTop: 4,
    marginBottom: 10,
  },
  githubLink: {
    flexDirection: 'row',
    alignItems: 'center',
    alignSelf: 'flex-start',
    backgroundColor: '#f3f4f6',
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 6,
  },
  githubIcon: {
    fontSize: 14,
    marginRight: 4,
  },
  githubText: {
    fontSize: 12,
    fontWeight: '600',
    color: '#2563eb',
  },
  footer: {
    alignItems: 'center',
    marginTop: 12,
    marginBottom: 20,
  },
  footerText: {
    color: '#888888',
    fontSize: 12,
  },
});
