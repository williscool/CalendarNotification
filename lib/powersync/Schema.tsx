// import { column, Schema, Table } from '@powersync/web';
import { column, Schema, Table } from '@powersync/react-native';

const eventsV9 = new Table(
  {
    // id column (text) is automatically included
    cid: column.integer,
    altm: column.integer,
    nid: column.integer,
    ttl: column.text,
    s1: column.text,
    estart: column.integer,
    eend: column.integer,
    istart: column.integer,
    iend: column.integer,
    loc: column.text,
    snz: column.integer,
    ls: column.integer,
    dsts: column.integer,
    clr: column.integer,
    rep: column.integer,
    alld: column.integer,
    ogn: column.integer,
    fsn: column.integer,
    attsts: column.integer,
    oattsts: column.integer,
    i1: column.integer,
    i2: column.integer,
    i3: column.integer,
    i4: column.integer,
    i5: column.integer,
    i6: column.integer,
    i7: column.integer,
    i8: column.integer,
    s2: column.text
  },
  { indexes: {} }
);

const reschedule_confirmations = new Table(
  {
    // id column (text) is automatically included
    event_id: column.integer,
    calendar_id: column.integer,
    original_instance_start_time: column.integer,
    title: column.text,
    new_instance_start_time: column.integer,
    
    // 0 past, 1 future
    // https://docs.powersync.com/usage/sync-rules/types#postgres-type-mapping
    // to quote the powersync docs: 
    // "There is no dedicated boolean data type. Boolean values are represented as 1 (true) or 0 (false)."
    is_in_future: column.integer,
    meta: column.text,
    created_at: column.text,
    updated_at: column.text
  },
  { indexes: {} }
);

export const AppSchema = new Schema({
  eventsV9,
  reschedule_confirmations
});

export type Database = (typeof AppSchema)['types'];
