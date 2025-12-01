import React, { useContext, useEffect, useState, useCallback, memo } from 'react';
import { StyleSheet, View, Text, FlatList, TouchableOpacity, RefreshControl } from 'react-native';
import { PowerSyncContext } from "@powersync/react";
import { useSyncDebug } from '@lib/hooks/SyncDebugContext';
import { SyncLogEntry, FailedOperation, LogFilterLevel } from '@lib/powersync/Connector';

const formatTimestamp = (ts: number): string => {
  const date = new Date(ts);
  return date.toLocaleTimeString() + '.' + String(date.getMilliseconds()).padStart(3, '0');
};

const LogLevelBadge: React.FC<{ level: SyncLogEntry['level'] }> = ({ level }) => {
  const colors: Record<SyncLogEntry['level'], string> = {
    error: '#FF3B30',
    warn: '#FF9500',
    info: '#007AFF',
    debug: '#8E8E93'
  };
  
  return (
    <View style={[styles.levelBadge, { backgroundColor: colors[level] }]}>
      <Text style={styles.levelBadgeText}>{level.toUpperCase()}</Text>
    </View>
  );
};

const LogEntryView = memo<{ entry: SyncLogEntry }>(({ entry }) => (
  <View style={styles.logEntry}>
    <View style={styles.logHeader}>
      <LogLevelBadge level={entry.level} />
      <Text style={styles.logTimestamp}>{formatTimestamp(entry.timestamp)}</Text>
    </View>
    <Text style={styles.logMessage} selectable>{entry.message}</Text>
    {entry.data && (
      <Text style={styles.logData} selectable>
        {JSON.stringify(entry.data, null, 2)}
      </Text>
    )}
  </View>
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
    <View style={styles.filterToggleContainer}>
      <Text style={styles.filterLabel}>Log Filter:</Text>
      <View style={styles.filterButtons}>
        {levels.map(({ key, label }) => (
          <TouchableOpacity
            key={key}
            style={[
              styles.filterButton,
              value === key && styles.filterButtonActive,
            ]}
            onPress={() => onChange(key)}
          >
            <Text style={[
              styles.filterButtonText,
              value === key && styles.filterButtonTextActive,
            ]}>
              {label}
            </Text>
          </TouchableOpacity>
        ))}
      </View>
      <Text style={styles.filterDescription}>
        {levels.find(l => l.key === value)?.description}
      </Text>
    </View>
  );
};

const FailedOperationView: React.FC<{ 
  op: FailedOperation; 
  onRemove: () => void;
}> = ({ op, onRemove }) => (
  <View style={styles.failedOp}>
    <View style={styles.failedOpHeader}>
      <Text style={styles.failedOpTitle}>{op.op} on {op.table}</Text>
      <Text style={styles.failedOpTimestamp}>{formatTimestamp(op.timestamp)}</Text>
    </View>
    <Text style={styles.failedOpId}>ID: {op.recordId}</Text>
    <Text style={styles.failedOpError}>Error: {op.error} ({op.errorCode})</Text>
    {op.opData && (
      <Text style={styles.failedOpData} selectable numberOfLines={3}>
        Data: {JSON.stringify(op.opData)}
      </Text>
    )}
    <TouchableOpacity style={styles.discardButton} onPress={onRemove}>
      <Text style={styles.discardButtonText}>Discard</Text>
    </TouchableOpacity>
  </View>
);

export const SyncDebug = () => {
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

  const renderLogItem = useCallback(({ item, index }: { item: SyncLogEntry; index: number }) => (
    <LogEntryView entry={item} />
  ), []);

  const keyExtractor = useCallback((item: SyncLogEntry, index: number) => 
    `${item.timestamp}-${index}`, []);

  const ListHeader = useCallback(() => (
    <>
      {/* Sync Status Section */}
      <View style={styles.section}>
        <Text style={styles.sectionTitle}>PowerSync Status</Text>
        <View style={styles.statusContainer}>
          <Text style={styles.statusText} selectable>{syncStatus}</Text>
        </View>
      </View>

      {/* Log Filter Toggle */}
      <View style={styles.section}>
        <LogFilterToggle value={logFilterLevel} onChange={setLogFilterLevel} />
      </View>

      {/* Failed Operations Section */}
      {failedOperations.length > 0 && (
        <View style={styles.section}>
          <View style={styles.sectionHeader}>
            <Text style={styles.sectionTitle}>Failed Operations ({failedOperations.length})</Text>
            <TouchableOpacity style={styles.clearButton} onPress={clearFailedOperations}>
              <Text style={styles.clearButtonText}>Clear All</Text>
            </TouchableOpacity>
          </View>
          <Text style={styles.sectionSubtitle}>
            These operations failed with unrecoverable errors. Review and discard when ready.
          </Text>
          {failedOperations.map((op) => (
            <FailedOperationView 
              key={op.id} 
              op={op} 
              onRemove={() => removeFailedOperation(op.id)} 
            />
          ))}
        </View>
      )}

      {/* Logs Section Header */}
      <View style={styles.logsHeader}>
        <View style={styles.sectionHeader}>
          <Text style={styles.sectionTitle}>Sync Logs ({logs.length})</Text>
          <TouchableOpacity style={styles.clearButton} onPress={clearLogs}>
            <Text style={styles.clearButtonText}>Clear</Text>
          </TouchableOpacity>
        </View>
      </View>
    </>
  ), [syncStatus, logFilterLevel, setLogFilterLevel, failedOperations, clearFailedOperations, removeFailedOperation, logs.length, clearLogs]);

  const ListEmpty = useCallback(() => (
    <View style={styles.emptyContainer}>
      <Text style={styles.emptyText}>No logs yet. Sync activity will appear here.</Text>
    </View>
  ), []);

  return (
    <FlatList
      style={styles.container}
      data={logs}
      renderItem={renderLogItem}
      keyExtractor={keyExtractor}
      ListHeaderComponent={ListHeader}
      ListEmptyComponent={ListEmpty}
      refreshControl={
        <RefreshControl refreshing={refreshing} onRefresh={onRefresh} />
      }
      // Performance optimizations
      removeClippedSubviews={true}
      maxToRenderPerBatch={20}
      windowSize={10}
      initialNumToRender={15}
      getItemLayout={undefined}  // Variable height items, can't use getItemLayout
      contentContainerStyle={styles.listContent}
    />
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5',
  },
  listContent: {
    paddingBottom: 20,
  },
  section: {
    backgroundColor: '#fff',
    padding: 16,
    marginBottom: 16,
    borderRadius: 8,
    margin: 16,
  },
  logsHeader: {
    backgroundColor: '#fff',
    padding: 16,
    paddingBottom: 8,
    marginHorizontal: 16,
    marginTop: 0,
    borderTopLeftRadius: 8,
    borderTopRightRadius: 8,
  },
  emptyContainer: {
    backgroundColor: '#fff',
    marginHorizontal: 16,
    paddingBottom: 16,
    borderBottomLeftRadius: 8,
    borderBottomRightRadius: 8,
  },
  sectionHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 8,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#333',
  },
  sectionSubtitle: {
    fontSize: 12,
    color: '#666',
    marginBottom: 12,
    fontStyle: 'italic',
  },
  statusContainer: {
    backgroundColor: '#f8f8f8',
    padding: 12,
    borderRadius: 6,
    marginTop: 8,
  },
  statusText: {
    fontFamily: 'monospace',
    fontSize: 12,
    color: '#333',
  },
  clearButton: {
    backgroundColor: '#FF3B30',
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 6,
  },
  clearButtonText: {
    color: '#fff',
    fontSize: 12,
    fontWeight: '600',
  },
  emptyText: {
    color: '#999',
    fontStyle: 'italic',
    textAlign: 'center',
    paddingVertical: 20,
  },
  logEntry: {
    backgroundColor: '#f8f8f8',
    padding: 10,
    borderRadius: 6,
    marginHorizontal: 16,
    marginTop: 8,
    borderLeftWidth: 3,
    borderLeftColor: '#ddd',
  },
  logHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 4,
  },
  levelBadge: {
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderRadius: 4,
    marginRight: 8,
  },
  levelBadgeText: {
    color: '#fff',
    fontSize: 10,
    fontWeight: '700',
  },
  logTimestamp: {
    fontSize: 11,
    color: '#999',
    fontFamily: 'monospace',
  },
  logMessage: {
    fontSize: 13,
    color: '#333',
    marginTop: 2,
  },
  logData: {
    fontSize: 11,
    color: '#666',
    fontFamily: 'monospace',
    backgroundColor: '#eee',
    padding: 6,
    borderRadius: 4,
    marginTop: 4,
  },
  failedOp: {
    backgroundColor: '#fff5f5',
    padding: 12,
    borderRadius: 6,
    marginTop: 8,
    borderLeftWidth: 3,
    borderLeftColor: '#FF3B30',
  },
  failedOpHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  failedOpTitle: {
    fontSize: 14,
    fontWeight: '600',
    color: '#333',
  },
  failedOpTimestamp: {
    fontSize: 11,
    color: '#999',
  },
  failedOpId: {
    fontSize: 12,
    color: '#666',
    marginTop: 4,
    fontFamily: 'monospace',
  },
  failedOpError: {
    fontSize: 12,
    color: '#FF3B30',
    marginTop: 4,
  },
  failedOpData: {
    fontSize: 11,
    color: '#666',
    fontFamily: 'monospace',
    backgroundColor: '#f8f0f0',
    padding: 6,
    borderRadius: 4,
    marginTop: 4,
  },
  discardButton: {
    backgroundColor: '#FF9500',
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 6,
    alignSelf: 'flex-end',
    marginTop: 8,
  },
  discardButtonText: {
    color: '#fff',
    fontSize: 12,
    fontWeight: '600',
  },
  filterToggleContainer: {
    alignItems: 'center',
  },
  filterLabel: {
    fontSize: 14,
    fontWeight: '600',
    color: '#333',
    marginBottom: 8,
  },
  filterButtons: {
    flexDirection: 'row',
    backgroundColor: '#f0f0f0',
    borderRadius: 8,
    padding: 4,
  },
  filterButton: {
    paddingHorizontal: 16,
    paddingVertical: 8,
    borderRadius: 6,
    marginHorizontal: 2,
  },
  filterButtonActive: {
    backgroundColor: '#007AFF',
  },
  filterButtonText: {
    fontSize: 13,
    fontWeight: '500',
    color: '#666',
  },
  filterButtonTextActive: {
    color: '#fff',
  },
  filterDescription: {
    fontSize: 11,
    color: '#999',
    marginTop: 6,
    fontStyle: 'italic',
  },
});

