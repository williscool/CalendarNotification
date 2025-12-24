import React, { useContext, useEffect, useState, useCallback, memo, useMemo } from 'react';
import { FlatList, RefreshControl } from 'react-native';
import { PowerSyncContext } from "@powersync/react";
import { useSyncDebug } from '@lib/hooks/SyncDebugContext';
import { SyncLogEntry, FailedOperation, LogFilterLevel } from '@lib/powersync/Connector';
import { Section } from '@lib/components/ui';
import { useTheme } from '@lib/theme/ThemeContext';
import { ThemeColors } from '@lib/theme/colors';
import { Box, Text, HStack, VStack, Button, ButtonText } from '@/components/ui';

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
    <Box 
      className="rounded px-1.5 py-0.5 mr-2"
      style={{ backgroundColor: logLevelColors[level] }}
    >
      <Text className="text-white text-xs font-bold">
        {level.toUpperCase()}
      </Text>
    </Box>
  );
};

const LogEntryView = memo<{ entry: SyncLogEntry; colors: ThemeColors }>(({ entry, colors }) => (
  <Box
    className="p-2.5 rounded-md mx-4 mt-2"
    style={{ 
      backgroundColor: colors.backgroundMuted,
      borderLeftWidth: 3,
      borderLeftColor: colors.border,
    }}
  >
    <HStack className="items-center mb-1">
      <LogLevelBadge level={entry.level} colors={colors} />
      <Text className="text-xs font-mono" style={{ color: colors.textLight }}>
        {formatTimestamp(entry.timestamp)}
      </Text>
    </HStack>
    <Text className="text-sm mt-0.5" style={{ color: colors.text }} selectable>
      {entry.message}
    </Text>
    {entry.data && (
      <Box className="p-1.5 rounded mt-1" style={{ backgroundColor: colors.borderLight }}>
        <Text className="text-xs font-mono" style={{ color: colors.textMuted }} selectable>
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
    <VStack className="items-center gap-2">
      <Text className="text-sm font-semibold" style={{ color: colors.text }}>
        Log Filter:
      </Text>
      <HStack className="rounded-lg p-1" style={{ backgroundColor: colors.backgroundMuted }}>
        {levels.map(({ key, label }) => (
          <Button
            key={key}
            onPress={() => onChange(key)}
            action={value === key ? 'primary' : 'secondary'}
            variant={value === key ? 'solid' : 'link'}
            size="sm"
            className="mx-0.5"
          >
            <ButtonText style={{ color: value === key ? '#fff' : colors.textMuted }}>
              {label}
            </ButtonText>
          </Button>
        ))}
      </HStack>
      <Text className="text-xs italic" style={{ color: colors.textLight }}>
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
    className="p-3 rounded-md mt-2"
    style={{ 
      backgroundColor: colors.failedOpBackground,
      borderLeftWidth: 3,
      borderLeftColor: colors.danger,
    }}
  >
    <HStack className="justify-between items-center">
      <Text className="text-sm font-semibold" style={{ color: colors.text }}>
        {op.op} on {op.table}
      </Text>
      <Text className="text-xs" style={{ color: colors.textLight }}>
        {formatTimestamp(op.timestamp)}
      </Text>
    </HStack>
    <Text className="text-xs font-mono mt-1" style={{ color: colors.textMuted }}>
      ID: {op.recordId}
    </Text>
    <Text className="text-xs mt-1" style={{ color: colors.danger }}>
      Error: {op.error} ({op.errorCode})
    </Text>
    {op.opData && (
      <Box className="p-1.5 rounded mt-1" style={{ backgroundColor: colors.failedOpDataBackground }}>
        <Text className="text-xs font-mono" style={{ color: colors.textMuted }} numberOfLines={3} selectable>
          Data: {JSON.stringify(op.opData)}
        </Text>
      </Box>
    )}
    <Button
      onPress={onRemove}
      action="secondary"
      size="xs"
      className="self-end mt-2"
      style={{ backgroundColor: colors.warning }}
    >
      <ButtonText className="text-white font-semibold">Discard</ButtonText>
    </Button>
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
        <Box className="p-3 rounded-md mt-2" style={{ backgroundColor: colors.backgroundMuted }}>
          <Text className="font-mono text-xs" style={{ color: colors.text }} selectable>
            {syncStatus}
          </Text>
        </Box>
      </Section>

      <Section>
        <LogFilterToggle value={logFilterLevel} onChange={setLogFilterLevel} colors={colors} />
      </Section>

      {failedOperations.length > 0 && (
        <Section>
          <HStack className="justify-between items-center mb-2">
            <Text className="text-lg font-bold" style={{ color: colors.text }}>
              Failed Operations ({failedOperations.length})
            </Text>
            <Button
              onPress={clearFailedOperations}
              action="negative"
              size="xs"
            >
              <ButtonText>Clear All</ButtonText>
            </Button>
          </HStack>
          <Text className="text-xs italic mb-3" style={{ color: colors.textMuted }}>
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
        className="p-4 pb-2 mx-4 rounded-t-lg"
        style={{ backgroundColor: colors.backgroundWhite }}
      >
        <HStack className="justify-between items-center mb-2">
          <VStack>
            <Text className="text-lg font-bold" style={{ color: colors.text }}>
              Sync Logs ({filteredLogs.length})
            </Text>
            {filteredLogs.length !== logs.length && (
              <Text className="text-xs" style={{ color: colors.textLight }}>
                {logs.length} total, filtered by level
              </Text>
            )}
          </VStack>
          <Button
            onPress={clearLogs}
            action="negative"
            size="xs"
          >
            <ButtonText>Clear</ButtonText>
          </Button>
        </HStack>
      </Box>
    </>
  ), [syncStatus, logFilterLevel, setLogFilterLevel, failedOperations, clearFailedOperations, removeFailedOperation, logs.length, filteredLogs.length, clearLogs, colors]);

  const ListEmpty = useCallback(() => (
    <Box className="mx-4 pb-4 rounded-b-lg" style={{ backgroundColor: colors.backgroundWhite }}>
      <Text className="italic text-center py-5" style={{ color: colors.textLight }}>
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
