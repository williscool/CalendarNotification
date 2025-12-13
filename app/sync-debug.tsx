import React, { useContext, useEffect, useState, useCallback, memo } from 'react';
import { FlatList, RefreshControl } from 'react-native';
import { Box, Text, HStack, VStack, Pressable, Badge, BadgeText } from '@gluestack-ui/themed';
import { PowerSyncContext } from "@powersync/react";
import { useSyncDebug } from '@lib/hooks/SyncDebugContext';
import { SyncLogEntry, FailedOperation, LogFilterLevel } from '@lib/powersync/Connector';
import { Section, ActionButton } from '@lib/components/ui';
import { colors } from '@lib/theme/colors';

const formatTimestamp = (ts: number): string => {
  const date = new Date(ts);
  return date.toLocaleTimeString() + '.' + String(date.getMilliseconds()).padStart(3, '0');
};

const logLevelColors: Record<SyncLogEntry['level'], string> = {
  error: colors.logError,
  warn: colors.logWarn,
  info: colors.logInfo,
  debug: colors.logDebug,
};

const LogLevelBadge: React.FC<{ level: SyncLogEntry['level'] }> = ({ level }) => (
  <Badge bg={logLevelColors[level]} borderRadius="$sm" px="$1.5" py="$0.5" mr="$2">
    <BadgeText color="#fff" fontSize="$2xs" fontWeight="$bold">
      {level.toUpperCase()}
    </BadgeText>
  </Badge>
);

const LogEntryView = memo<{ entry: SyncLogEntry }>(({ entry }) => (
  <Box
    bg={colors.backgroundMuted}
    p="$2.5"
    borderRadius="$md"
    mx="$4"
    mt="$2"
    borderLeftWidth={3}
    borderLeftColor={colors.border}
  >
    <HStack alignItems="center" mb="$1">
      <LogLevelBadge level={entry.level} />
      <Text fontSize="$2xs" color={colors.textLight} fontFamily="monospace">
        {formatTimestamp(entry.timestamp)}
      </Text>
    </HStack>
    <Text fontSize="$sm" color={colors.text} mt="$0.5" selectable>
      {entry.message}
    </Text>
    {entry.data && (
      <Box bg={colors.borderLight} p="$1.5" borderRadius="$sm" mt="$1">
        <Text fontSize="$2xs" color={colors.textMuted} fontFamily="monospace" selectable>
          {JSON.stringify(entry.data, null, 2)}
        </Text>
      </Box>
    )}
  </Box>
));

const LogFilterToggle: React.FC<{
  value: LogFilterLevel;
  onChange: (level: LogFilterLevel) => void;
}> = ({ value, onChange }) => {
  const levels: { key: LogFilterLevel; label: string; description: string }[] = [
    { key: 'info', label: 'Info', description: 'PowerSync only' },
    { key: 'debug', label: 'Debug', description: '+ Supabase & sync' },
    { key: 'firehose', label: 'Firehose', description: 'Everything' },
  ];

  return (
    <VStack alignItems="center" space="sm">
      <Text fontSize="$sm" fontWeight="$semibold" color={colors.text}>
        Log Filter:
      </Text>
      <HStack bg="#f0f0f0" borderRadius="$lg" p="$1">
        {levels.map(({ key, label }) => (
          <Pressable
            key={key}
            onPress={() => onChange(key)}
            px="$4"
            py="$2"
            borderRadius="$md"
            mx="$0.5"
            bg={value === key ? colors.primary : 'transparent'}
          >
            <Text
              fontSize="$sm"
              fontWeight="$medium"
              color={value === key ? '#fff' : colors.textMuted}
            >
              {label}
            </Text>
          </Pressable>
        ))}
      </HStack>
      <Text fontSize="$2xs" color={colors.textLight} fontStyle="italic">
        {levels.find(l => l.key === value)?.description}
      </Text>
    </VStack>
  );
};

const FailedOperationView: React.FC<{
  op: FailedOperation;
  onRemove: () => void;
}> = ({ op, onRemove }) => (
  <Box
    bg="#fff5f5"
    p="$3"
    borderRadius="$md"
    mt="$2"
    borderLeftWidth={3}
    borderLeftColor={colors.danger}
  >
    <HStack justifyContent="space-between" alignItems="center">
      <Text fontSize="$sm" fontWeight="$semibold" color={colors.text}>
        {op.op} on {op.table}
      </Text>
      <Text fontSize="$2xs" color={colors.textLight}>
        {formatTimestamp(op.timestamp)}
      </Text>
    </HStack>
    <Text fontSize="$xs" color={colors.textMuted} mt="$1" fontFamily="monospace">
      ID: {op.recordId}
    </Text>
    <Text fontSize="$xs" color={colors.danger} mt="$1">
      Error: {op.error} ({op.errorCode})
    </Text>
    {op.opData && (
      <Box bg="#f8f0f0" p="$1.5" borderRadius="$sm" mt="$1">
        <Text fontSize="$2xs" color={colors.textMuted} fontFamily="monospace" numberOfLines={3} selectable>
          Data: {JSON.stringify(op.opData)}
        </Text>
      </Box>
    )}
    <Pressable
      onPress={onRemove}
      bg={colors.warning}
      px="$3"
      py="$1.5"
      borderRadius="$md"
      alignSelf="flex-end"
      mt="$2"
    >
      <Text color="#fff" fontSize="$xs" fontWeight="$semibold">
        Discard
      </Text>
    </Pressable>
  </Box>
);

export default function SyncDebug() {
  const providerDb = useContext(PowerSyncContext);
  const { logs, failedOperations, logFilterLevel, setLogFilterLevel, clearLogs, refreshFailedOperations, removeFailedOperation, clearFailedOperations } = useSyncDebug();
  const [syncStatus, setSyncStatus] = useState<string>('{}');
  const [refreshing, setRefreshing] = useState(false);

  useEffect(() => {
    const interval = setInterval(() => {
      if (providerDb) {
        setSyncStatus(JSON.stringify(providerDb.currentStatus, null, 2));
      }
    }, 1000);
    return () => clearInterval(interval);
  }, [providerDb]);

  const onRefresh = async () => {
    setRefreshing(true);
    await refreshFailedOperations();
    setRefreshing(false);
  };

  const renderLogItem = useCallback(({ item }: { item: SyncLogEntry }) => (
    <LogEntryView entry={item} />
  ), []);

  const keyExtractor = useCallback((item: SyncLogEntry, index: number) =>
    `${item.timestamp}-${index}`, []);

  const ListHeader = useCallback(() => (
    <>
      <Section title="PowerSync Status">
        <Box bg={colors.backgroundMuted} p="$3" borderRadius="$md" mt="$2">
          <Text fontFamily="monospace" fontSize="$xs" color={colors.text} selectable>
            {syncStatus}
          </Text>
        </Box>
      </Section>

      <Section>
        <LogFilterToggle value={logFilterLevel} onChange={setLogFilterLevel} />
      </Section>

      {failedOperations.length > 0 && (
        <Section>
          <HStack justifyContent="space-between" alignItems="center" mb="$2">
            <Text fontSize="$lg" fontWeight="$bold" color={colors.text}>
              Failed Operations ({failedOperations.length})
            </Text>
            <Pressable onPress={clearFailedOperations} bg={colors.danger} px="$3" py="$1.5" borderRadius="$md">
              <Text color="#fff" fontSize="$xs" fontWeight="$semibold">Clear All</Text>
            </Pressable>
          </HStack>
          <Text fontSize="$xs" color={colors.textMuted} fontStyle="italic" mb="$3">
            These operations failed with unrecoverable errors. Review and discard when ready.
          </Text>
          {failedOperations.map((op) => (
            <FailedOperationView
              key={op.id}
              op={op}
              onRemove={() => removeFailedOperation(op.id)}
            />
          ))}
        </Section>
      )}

      <Box
        bg={colors.backgroundWhite}
        p="$4"
        pb="$2"
        mx="$4"
        borderTopLeftRadius="$lg"
        borderTopRightRadius="$lg"
      >
        <HStack justifyContent="space-between" alignItems="center" mb="$2">
          <Text fontSize="$lg" fontWeight="$bold" color={colors.text}>
            Sync Logs ({logs.length})
          </Text>
          <Pressable onPress={clearLogs} bg={colors.danger} px="$3" py="$1.5" borderRadius="$md">
            <Text color="#fff" fontSize="$xs" fontWeight="$semibold">Clear</Text>
          </Pressable>
        </HStack>
      </Box>
    </>
  ), [syncStatus, logFilterLevel, setLogFilterLevel, failedOperations, clearFailedOperations, removeFailedOperation, logs.length, clearLogs]);

  const ListEmpty = useCallback(() => (
    <Box bg={colors.backgroundWhite} mx="$4" pb="$4" borderBottomLeftRadius="$lg" borderBottomRightRadius="$lg">
      <Text color={colors.textLight} fontStyle="italic" textAlign="center" py="$5">
        No logs yet. Sync activity will appear here.
      </Text>
    </Box>
  ), []);

  return (
    <FlatList
      style={{ flex: 1, backgroundColor: colors.background }}
      data={logs}
      renderItem={renderLogItem}
      keyExtractor={keyExtractor}
      ListHeaderComponent={ListHeader}
      ListEmptyComponent={ListEmpty}
      refreshControl={
        <RefreshControl refreshing={refreshing} onRefresh={onRefresh} />
      }
      // Performance optimizations - kept from original
      removeClippedSubviews={true}
      maxToRenderPerBatch={20}
      windowSize={10}
      initialNumToRender={15}
      getItemLayout={undefined}
      contentContainerStyle={{ paddingBottom: 20 }}
    />
  );
}
