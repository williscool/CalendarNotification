import React, { useContext, useEffect, useState, useCallback, memo, useMemo } from 'react';
import { FlatList, RefreshControl } from 'react-native';
import { Box, Text, HStack, VStack, Pressable, Badge, BadgeText } from '@gluestack-ui/themed';
import { PowerSyncContext } from "@powersync/react";
import { useSyncDebug } from '@lib/hooks/SyncDebugContext';
import { SyncLogEntry, FailedOperation, LogFilterLevel } from '@lib/powersync/Connector';
import { Section, ActionButton } from '@lib/components/ui';
import { useTheme } from '@lib/theme/ThemeContext';
import { ThemeColors } from '@lib/theme/colors';

// Filter logs based on display level preference
const filterLogsByLevel = (logs: SyncLogEntry[], filterLevel: LogFilterLevel): SyncLogEntry[] => {
  if (filterLevel === 'firehose') return logs; // Show everything
  
  // For 'info' and 'debug', filter by log level (not logger name anymore)
  const allowedLevels: Set<SyncLogEntry['level']> = filterLevel === 'info'
    ? new Set(['error', 'warn', 'info'])
    : new Set(['error', 'warn', 'info', 'debug']);
  
  return logs.filter(log => allowedLevels.has(log.level));
};

const formatTimestamp = (ts: number): string => {
  const date = new Date(ts);
  return date.toLocaleTimeString() + '.' + String(date.getMilliseconds()).padStart(3, '0');
};

const LogLevelBadge: React.FC<{ level: SyncLogEntry['level']; colors: ThemeColors }> = ({ level, colors }) => {
  const logLevelColors: Record<SyncLogEntry['level'], string> = {
    error: colors.logError,
    warn: colors.logWarn,
    info: colors.logInfo,
    debug: colors.logDebug,
  };
  
  return (
    <Badge bg={logLevelColors[level]} borderRadius="$sm" px="$1.5" py="$0.5" mr="$2">
      <BadgeText color="#fff" fontSize="$2xs" fontWeight="$bold">
        {level.toUpperCase()}
      </BadgeText>
    </Badge>
  );
};

const LogEntryView = memo<{ entry: SyncLogEntry; colors: ThemeColors }>(({ entry, colors }) => (
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
      <LogLevelBadge level={entry.level} colors={colors} />
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
  colors: ThemeColors;
}> = ({ value, onChange, colors }) => {
  const levels: { key: LogFilterLevel; label: string; description: string }[] = [
    { key: 'info', label: 'Info', description: 'Errors, warnings & info' },
    { key: 'debug', label: 'Debug', description: '+ debug messages' },
    { key: 'firehose', label: 'Firehose', description: 'Everything (all levels)' },
  ];

  return (
    <VStack alignItems="center" space="sm">
      <Text fontSize="$sm" fontWeight="$semibold" color={colors.text}>
        Log Filter:
      </Text>
      <HStack bg={colors.backgroundMuted} borderRadius="$lg" p="$1">
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
  colors: ThemeColors;
}> = ({ op, onRemove, colors }) => (
  <Box
    bg={colors.failedOpBackground}
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
      <Box bg={colors.failedOpDataBackground} p="$1.5" borderRadius="$sm" mt="$1">
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
  const { colors } = useTheme();
  const providerDb = useContext(PowerSyncContext);
  const { logs, logsVersion, failedOperations, logFilterLevel, setLogFilterLevel, clearLogs, refreshFailedOperations, removeFailedOperation, clearFailedOperations } = useSyncDebug();
  const [syncStatus, setSyncStatus] = useState<string>('{}');
  const [refreshing, setRefreshing] = useState(false);

  // Filter logs for display based on current filter level
  // Include logsVersion in deps to recalculate when logs update
  const filteredLogs = useMemo(
    () => filterLogsByLevel(logs, logFilterLevel),
    [logs, logFilterLevel, logsVersion]
  );

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
    <LogEntryView entry={item} colors={colors} />
  ), [colors]);

  const keyExtractor = useCallback((item: SyncLogEntry) => item.id, []);

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
        <LogFilterToggle value={logFilterLevel} onChange={setLogFilterLevel} colors={colors} />
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
              colors={colors}
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
          <VStack>
            <Text fontSize="$lg" fontWeight="$bold" color={colors.text}>
              Sync Logs ({filteredLogs.length})
            </Text>
            {filteredLogs.length !== logs.length && (
              <Text fontSize="$2xs" color={colors.textLight}>
                {logs.length} total, filtered by level
              </Text>
            )}
          </VStack>
          <Pressable onPress={clearLogs} bg={colors.danger} px="$3" py="$1.5" borderRadius="$md">
            <Text color="#fff" fontSize="$xs" fontWeight="$semibold">Clear</Text>
          </Pressable>
        </HStack>
      </Box>
    </>
  ), [syncStatus, logFilterLevel, setLogFilterLevel, failedOperations, clearFailedOperations, removeFailedOperation, logs.length, filteredLogs.length, clearLogs, colors]);

  const ListEmpty = useCallback(() => (
    <Box bg={colors.backgroundWhite} mx="$4" pb="$4" borderBottomLeftRadius="$lg" borderBottomRightRadius="$lg">
      <Text color={colors.textLight} fontStyle="italic" textAlign="center" py="$5">
        No logs yet. Sync activity will appear here.
      </Text>
    </Box>
  ), [colors]);

  return (
    <FlatList
      style={{ flex: 1, backgroundColor: colors.background }}
      data={filteredLogs}
      extraData={logsVersion}  // Triggers re-render when logs update
      renderItem={renderLogItem}
      keyExtractor={keyExtractor}
      ListHeaderComponent={ListHeader}
      ListEmptyComponent={ListEmpty}
      refreshControl={
        <RefreshControl refreshing={refreshing} onRefresh={onRefresh} />
      }
      removeClippedSubviews={true}
      maxToRenderPerBatch={20}
      windowSize={10}
      initialNumToRender={15}
      contentContainerStyle={{ paddingBottom: 20 }}
    />
  );
}
