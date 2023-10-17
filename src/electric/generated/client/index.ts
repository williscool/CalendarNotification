import { z } from 'zod';
import type { Prisma } from '@prisma/client';
import { TableSchema, DbSchema, Relation, ElectricClient, HKT } from 'electric-sql/dist/client/model';
import migrations from './migrations';

/////////////////////////////////////////
// HELPER FUNCTIONS
/////////////////////////////////////////


/////////////////////////////////////////
// ENUMS
/////////////////////////////////////////

export const EventsV9ScalarFieldEnumSchema = z.enum(['cid','id','altm','nid','ttl','s1','estart','eend','istart','iend','loc','snz','ls','dsts','clr','rep','alld','ogn','fsn','attsts','oattsts','i1','i2','i3','i4','i5','i6','i7','i8','s2']);

export const ItemsScalarFieldEnumSchema = z.enum(['value']);

export const QueryModeSchema = z.enum(['default','insensitive']);

export const SortOrderSchema = z.enum(['asc','desc']);

export const TransactionIsolationLevelSchema = z.enum(['ReadUncommitted','ReadCommitted','RepeatableRead','Serializable']);
/////////////////////////////////////////
// MODELS
/////////////////////////////////////////

/////////////////////////////////////////
// EVENTS V 9 SCHEMA
/////////////////////////////////////////

export const EventsV9Schema = z.object({
  cid: z.number().int().nullable(),
  id: z.number().int(),
  altm: z.number().int().nullable(),
  nid: z.number().int().nullable(),
  ttl: z.string().nullable(),
  s1: z.string().nullable(),
  estart: z.number().int().nullable(),
  eend: z.number().int().nullable(),
  istart: z.number().int(),
  iend: z.number().int().nullable(),
  loc: z.string().nullable(),
  snz: z.number().int().nullable(),
  ls: z.number().int().nullable(),
  dsts: z.number().int().nullable(),
  clr: z.number().int().nullable(),
  rep: z.number().int().nullable(),
  alld: z.number().int().nullable(),
  ogn: z.number().int().nullable(),
  fsn: z.number().int().nullable(),
  attsts: z.number().int().nullable(),
  oattsts: z.number().int().nullable(),
  i1: z.number().int().nullable(),
  i2: z.number().int().nullable(),
  i3: z.number().int().nullable(),
  i4: z.number().int().nullable(),
  i5: z.number().int().nullable(),
  i6: z.number().int().nullable(),
  i7: z.number().int().nullable(),
  i8: z.number().int().nullable(),
  s2: z.string().nullable(),
})

export type EventsV9 = z.infer<typeof EventsV9Schema>

/////////////////////////////////////////
// ITEMS SCHEMA
/////////////////////////////////////////

export const ItemsSchema = z.object({
  value: z.string(),
})

export type Items = z.infer<typeof ItemsSchema>

/////////////////////////////////////////
// SELECT & INCLUDE
/////////////////////////////////////////

// EVENTS V 9
//------------------------------------------------------

export const EventsV9SelectSchema: z.ZodType<Prisma.EventsV9Select> = z.object({
  cid: z.boolean().optional(),
  id: z.boolean().optional(),
  altm: z.boolean().optional(),
  nid: z.boolean().optional(),
  ttl: z.boolean().optional(),
  s1: z.boolean().optional(),
  estart: z.boolean().optional(),
  eend: z.boolean().optional(),
  istart: z.boolean().optional(),
  iend: z.boolean().optional(),
  loc: z.boolean().optional(),
  snz: z.boolean().optional(),
  ls: z.boolean().optional(),
  dsts: z.boolean().optional(),
  clr: z.boolean().optional(),
  rep: z.boolean().optional(),
  alld: z.boolean().optional(),
  ogn: z.boolean().optional(),
  fsn: z.boolean().optional(),
  attsts: z.boolean().optional(),
  oattsts: z.boolean().optional(),
  i1: z.boolean().optional(),
  i2: z.boolean().optional(),
  i3: z.boolean().optional(),
  i4: z.boolean().optional(),
  i5: z.boolean().optional(),
  i6: z.boolean().optional(),
  i7: z.boolean().optional(),
  i8: z.boolean().optional(),
  s2: z.boolean().optional(),
}).strict()

// ITEMS
//------------------------------------------------------

export const ItemsSelectSchema: z.ZodType<Prisma.ItemsSelect> = z.object({
  value: z.boolean().optional(),
}).strict()


/////////////////////////////////////////
// INPUT TYPES
/////////////////////////////////////////

export const EventsV9WhereInputSchema: z.ZodType<Prisma.EventsV9WhereInput> = z.object({
  AND: z.union([ z.lazy(() => EventsV9WhereInputSchema),z.lazy(() => EventsV9WhereInputSchema).array() ]).optional(),
  OR: z.lazy(() => EventsV9WhereInputSchema).array().optional(),
  NOT: z.union([ z.lazy(() => EventsV9WhereInputSchema),z.lazy(() => EventsV9WhereInputSchema).array() ]).optional(),
  cid: z.union([ z.lazy(() => IntNullableFilterSchema),z.number() ]).optional().nullable(),
  id: z.union([ z.lazy(() => IntFilterSchema),z.number() ]).optional(),
  altm: z.union([ z.lazy(() => IntNullableFilterSchema),z.number() ]).optional().nullable(),
  nid: z.union([ z.lazy(() => IntNullableFilterSchema),z.number() ]).optional().nullable(),
  ttl: z.union([ z.lazy(() => StringNullableFilterSchema),z.string() ]).optional().nullable(),
  s1: z.union([ z.lazy(() => StringNullableFilterSchema),z.string() ]).optional().nullable(),
  estart: z.union([ z.lazy(() => IntNullableFilterSchema),z.number() ]).optional().nullable(),
  eend: z.union([ z.lazy(() => IntNullableFilterSchema),z.number() ]).optional().nullable(),
  istart: z.union([ z.lazy(() => IntFilterSchema),z.number() ]).optional(),
  iend: z.union([ z.lazy(() => IntNullableFilterSchema),z.number() ]).optional().nullable(),
  loc: z.union([ z.lazy(() => StringNullableFilterSchema),z.string() ]).optional().nullable(),
  snz: z.union([ z.lazy(() => IntNullableFilterSchema),z.number() ]).optional().nullable(),
  ls: z.union([ z.lazy(() => IntNullableFilterSchema),z.number() ]).optional().nullable(),
  dsts: z.union([ z.lazy(() => IntNullableFilterSchema),z.number() ]).optional().nullable(),
  clr: z.union([ z.lazy(() => IntNullableFilterSchema),z.number() ]).optional().nullable(),
  rep: z.union([ z.lazy(() => IntNullableFilterSchema),z.number() ]).optional().nullable(),
  alld: z.union([ z.lazy(() => IntNullableFilterSchema),z.number() ]).optional().nullable(),
  ogn: z.union([ z.lazy(() => IntNullableFilterSchema),z.number() ]).optional().nullable(),
  fsn: z.union([ z.lazy(() => IntNullableFilterSchema),z.number() ]).optional().nullable(),
  attsts: z.union([ z.lazy(() => IntNullableFilterSchema),z.number() ]).optional().nullable(),
  oattsts: z.union([ z.lazy(() => IntNullableFilterSchema),z.number() ]).optional().nullable(),
  i1: z.union([ z.lazy(() => IntNullableFilterSchema),z.number() ]).optional().nullable(),
  i2: z.union([ z.lazy(() => IntNullableFilterSchema),z.number() ]).optional().nullable(),
  i3: z.union([ z.lazy(() => IntNullableFilterSchema),z.number() ]).optional().nullable(),
  i4: z.union([ z.lazy(() => IntNullableFilterSchema),z.number() ]).optional().nullable(),
  i5: z.union([ z.lazy(() => IntNullableFilterSchema),z.number() ]).optional().nullable(),
  i6: z.union([ z.lazy(() => IntNullableFilterSchema),z.number() ]).optional().nullable(),
  i7: z.union([ z.lazy(() => IntNullableFilterSchema),z.number() ]).optional().nullable(),
  i8: z.union([ z.lazy(() => IntNullableFilterSchema),z.number() ]).optional().nullable(),
  s2: z.union([ z.lazy(() => StringNullableFilterSchema),z.string() ]).optional().nullable(),
}).strict();

export const EventsV9OrderByWithRelationInputSchema: z.ZodType<Prisma.EventsV9OrderByWithRelationInput> = z.object({
  cid: z.lazy(() => SortOrderSchema).optional(),
  id: z.lazy(() => SortOrderSchema).optional(),
  altm: z.lazy(() => SortOrderSchema).optional(),
  nid: z.lazy(() => SortOrderSchema).optional(),
  ttl: z.lazy(() => SortOrderSchema).optional(),
  s1: z.lazy(() => SortOrderSchema).optional(),
  estart: z.lazy(() => SortOrderSchema).optional(),
  eend: z.lazy(() => SortOrderSchema).optional(),
  istart: z.lazy(() => SortOrderSchema).optional(),
  iend: z.lazy(() => SortOrderSchema).optional(),
  loc: z.lazy(() => SortOrderSchema).optional(),
  snz: z.lazy(() => SortOrderSchema).optional(),
  ls: z.lazy(() => SortOrderSchema).optional(),
  dsts: z.lazy(() => SortOrderSchema).optional(),
  clr: z.lazy(() => SortOrderSchema).optional(),
  rep: z.lazy(() => SortOrderSchema).optional(),
  alld: z.lazy(() => SortOrderSchema).optional(),
  ogn: z.lazy(() => SortOrderSchema).optional(),
  fsn: z.lazy(() => SortOrderSchema).optional(),
  attsts: z.lazy(() => SortOrderSchema).optional(),
  oattsts: z.lazy(() => SortOrderSchema).optional(),
  i1: z.lazy(() => SortOrderSchema).optional(),
  i2: z.lazy(() => SortOrderSchema).optional(),
  i3: z.lazy(() => SortOrderSchema).optional(),
  i4: z.lazy(() => SortOrderSchema).optional(),
  i5: z.lazy(() => SortOrderSchema).optional(),
  i6: z.lazy(() => SortOrderSchema).optional(),
  i7: z.lazy(() => SortOrderSchema).optional(),
  i8: z.lazy(() => SortOrderSchema).optional(),
  s2: z.lazy(() => SortOrderSchema).optional()
}).strict();

export const EventsV9WhereUniqueInputSchema: z.ZodType<Prisma.EventsV9WhereUniqueInput> = z.object({
  id_istart: z.lazy(() => EventsV9IdIstartCompoundUniqueInputSchema).optional(),
  id_istart: z.lazy(() => EventsV9IdIstartCompoundUniqueInputSchema).optional()
}).strict();

export const EventsV9OrderByWithAggregationInputSchema: z.ZodType<Prisma.EventsV9OrderByWithAggregationInput> = z.object({
  cid: z.lazy(() => SortOrderSchema).optional(),
  id: z.lazy(() => SortOrderSchema).optional(),
  altm: z.lazy(() => SortOrderSchema).optional(),
  nid: z.lazy(() => SortOrderSchema).optional(),
  ttl: z.lazy(() => SortOrderSchema).optional(),
  s1: z.lazy(() => SortOrderSchema).optional(),
  estart: z.lazy(() => SortOrderSchema).optional(),
  eend: z.lazy(() => SortOrderSchema).optional(),
  istart: z.lazy(() => SortOrderSchema).optional(),
  iend: z.lazy(() => SortOrderSchema).optional(),
  loc: z.lazy(() => SortOrderSchema).optional(),
  snz: z.lazy(() => SortOrderSchema).optional(),
  ls: z.lazy(() => SortOrderSchema).optional(),
  dsts: z.lazy(() => SortOrderSchema).optional(),
  clr: z.lazy(() => SortOrderSchema).optional(),
  rep: z.lazy(() => SortOrderSchema).optional(),
  alld: z.lazy(() => SortOrderSchema).optional(),
  ogn: z.lazy(() => SortOrderSchema).optional(),
  fsn: z.lazy(() => SortOrderSchema).optional(),
  attsts: z.lazy(() => SortOrderSchema).optional(),
  oattsts: z.lazy(() => SortOrderSchema).optional(),
  i1: z.lazy(() => SortOrderSchema).optional(),
  i2: z.lazy(() => SortOrderSchema).optional(),
  i3: z.lazy(() => SortOrderSchema).optional(),
  i4: z.lazy(() => SortOrderSchema).optional(),
  i5: z.lazy(() => SortOrderSchema).optional(),
  i6: z.lazy(() => SortOrderSchema).optional(),
  i7: z.lazy(() => SortOrderSchema).optional(),
  i8: z.lazy(() => SortOrderSchema).optional(),
  s2: z.lazy(() => SortOrderSchema).optional(),
  _count: z.lazy(() => EventsV9CountOrderByAggregateInputSchema).optional(),
  _avg: z.lazy(() => EventsV9AvgOrderByAggregateInputSchema).optional(),
  _max: z.lazy(() => EventsV9MaxOrderByAggregateInputSchema).optional(),
  _min: z.lazy(() => EventsV9MinOrderByAggregateInputSchema).optional(),
  _sum: z.lazy(() => EventsV9SumOrderByAggregateInputSchema).optional()
}).strict();

export const EventsV9ScalarWhereWithAggregatesInputSchema: z.ZodType<Prisma.EventsV9ScalarWhereWithAggregatesInput> = z.object({
  AND: z.union([ z.lazy(() => EventsV9ScalarWhereWithAggregatesInputSchema),z.lazy(() => EventsV9ScalarWhereWithAggregatesInputSchema).array() ]).optional(),
  OR: z.lazy(() => EventsV9ScalarWhereWithAggregatesInputSchema).array().optional(),
  NOT: z.union([ z.lazy(() => EventsV9ScalarWhereWithAggregatesInputSchema),z.lazy(() => EventsV9ScalarWhereWithAggregatesInputSchema).array() ]).optional(),
  cid: z.union([ z.lazy(() => IntNullableWithAggregatesFilterSchema),z.number() ]).optional().nullable(),
  id: z.union([ z.lazy(() => IntWithAggregatesFilterSchema),z.number() ]).optional(),
  altm: z.union([ z.lazy(() => IntNullableWithAggregatesFilterSchema),z.number() ]).optional().nullable(),
  nid: z.union([ z.lazy(() => IntNullableWithAggregatesFilterSchema),z.number() ]).optional().nullable(),
  ttl: z.union([ z.lazy(() => StringNullableWithAggregatesFilterSchema),z.string() ]).optional().nullable(),
  s1: z.union([ z.lazy(() => StringNullableWithAggregatesFilterSchema),z.string() ]).optional().nullable(),
  estart: z.union([ z.lazy(() => IntNullableWithAggregatesFilterSchema),z.number() ]).optional().nullable(),
  eend: z.union([ z.lazy(() => IntNullableWithAggregatesFilterSchema),z.number() ]).optional().nullable(),
  istart: z.union([ z.lazy(() => IntWithAggregatesFilterSchema),z.number() ]).optional(),
  iend: z.union([ z.lazy(() => IntNullableWithAggregatesFilterSchema),z.number() ]).optional().nullable(),
  loc: z.union([ z.lazy(() => StringNullableWithAggregatesFilterSchema),z.string() ]).optional().nullable(),
  snz: z.union([ z.lazy(() => IntNullableWithAggregatesFilterSchema),z.number() ]).optional().nullable(),
  ls: z.union([ z.lazy(() => IntNullableWithAggregatesFilterSchema),z.number() ]).optional().nullable(),
  dsts: z.union([ z.lazy(() => IntNullableWithAggregatesFilterSchema),z.number() ]).optional().nullable(),
  clr: z.union([ z.lazy(() => IntNullableWithAggregatesFilterSchema),z.number() ]).optional().nullable(),
  rep: z.union([ z.lazy(() => IntNullableWithAggregatesFilterSchema),z.number() ]).optional().nullable(),
  alld: z.union([ z.lazy(() => IntNullableWithAggregatesFilterSchema),z.number() ]).optional().nullable(),
  ogn: z.union([ z.lazy(() => IntNullableWithAggregatesFilterSchema),z.number() ]).optional().nullable(),
  fsn: z.union([ z.lazy(() => IntNullableWithAggregatesFilterSchema),z.number() ]).optional().nullable(),
  attsts: z.union([ z.lazy(() => IntNullableWithAggregatesFilterSchema),z.number() ]).optional().nullable(),
  oattsts: z.union([ z.lazy(() => IntNullableWithAggregatesFilterSchema),z.number() ]).optional().nullable(),
  i1: z.union([ z.lazy(() => IntNullableWithAggregatesFilterSchema),z.number() ]).optional().nullable(),
  i2: z.union([ z.lazy(() => IntNullableWithAggregatesFilterSchema),z.number() ]).optional().nullable(),
  i3: z.union([ z.lazy(() => IntNullableWithAggregatesFilterSchema),z.number() ]).optional().nullable(),
  i4: z.union([ z.lazy(() => IntNullableWithAggregatesFilterSchema),z.number() ]).optional().nullable(),
  i5: z.union([ z.lazy(() => IntNullableWithAggregatesFilterSchema),z.number() ]).optional().nullable(),
  i6: z.union([ z.lazy(() => IntNullableWithAggregatesFilterSchema),z.number() ]).optional().nullable(),
  i7: z.union([ z.lazy(() => IntNullableWithAggregatesFilterSchema),z.number() ]).optional().nullable(),
  i8: z.union([ z.lazy(() => IntNullableWithAggregatesFilterSchema),z.number() ]).optional().nullable(),
  s2: z.union([ z.lazy(() => StringNullableWithAggregatesFilterSchema),z.string() ]).optional().nullable(),
}).strict();

export const ItemsWhereInputSchema: z.ZodType<Prisma.ItemsWhereInput> = z.object({
  AND: z.union([ z.lazy(() => ItemsWhereInputSchema),z.lazy(() => ItemsWhereInputSchema).array() ]).optional(),
  OR: z.lazy(() => ItemsWhereInputSchema).array().optional(),
  NOT: z.union([ z.lazy(() => ItemsWhereInputSchema),z.lazy(() => ItemsWhereInputSchema).array() ]).optional(),
  value: z.union([ z.lazy(() => StringFilterSchema),z.string() ]).optional(),
}).strict();

export const ItemsOrderByWithRelationInputSchema: z.ZodType<Prisma.ItemsOrderByWithRelationInput> = z.object({
  value: z.lazy(() => SortOrderSchema).optional()
}).strict();

export const ItemsWhereUniqueInputSchema: z.ZodType<Prisma.ItemsWhereUniqueInput> = z.object({
  value: z.string().optional()
}).strict();

export const ItemsOrderByWithAggregationInputSchema: z.ZodType<Prisma.ItemsOrderByWithAggregationInput> = z.object({
  value: z.lazy(() => SortOrderSchema).optional(),
  _count: z.lazy(() => ItemsCountOrderByAggregateInputSchema).optional(),
  _max: z.lazy(() => ItemsMaxOrderByAggregateInputSchema).optional(),
  _min: z.lazy(() => ItemsMinOrderByAggregateInputSchema).optional()
}).strict();

export const ItemsScalarWhereWithAggregatesInputSchema: z.ZodType<Prisma.ItemsScalarWhereWithAggregatesInput> = z.object({
  AND: z.union([ z.lazy(() => ItemsScalarWhereWithAggregatesInputSchema),z.lazy(() => ItemsScalarWhereWithAggregatesInputSchema).array() ]).optional(),
  OR: z.lazy(() => ItemsScalarWhereWithAggregatesInputSchema).array().optional(),
  NOT: z.union([ z.lazy(() => ItemsScalarWhereWithAggregatesInputSchema),z.lazy(() => ItemsScalarWhereWithAggregatesInputSchema).array() ]).optional(),
  value: z.union([ z.lazy(() => StringWithAggregatesFilterSchema),z.string() ]).optional(),
}).strict();

export const EventsV9CreateInputSchema: z.ZodType<Prisma.EventsV9CreateInput> = z.object({
  cid: z.number().int().optional().nullable(),
  id: z.number().int(),
  altm: z.number().int().optional().nullable(),
  nid: z.number().int().optional().nullable(),
  ttl: z.string().optional().nullable(),
  s1: z.string().optional().nullable(),
  estart: z.number().int().optional().nullable(),
  eend: z.number().int().optional().nullable(),
  istart: z.number().int(),
  iend: z.number().int().optional().nullable(),
  loc: z.string().optional().nullable(),
  snz: z.number().int().optional().nullable(),
  ls: z.number().int().optional().nullable(),
  dsts: z.number().int().optional().nullable(),
  clr: z.number().int().optional().nullable(),
  rep: z.number().int().optional().nullable(),
  alld: z.number().int().optional().nullable(),
  ogn: z.number().int().optional().nullable(),
  fsn: z.number().int().optional().nullable(),
  attsts: z.number().int().optional().nullable(),
  oattsts: z.number().int().optional().nullable(),
  i1: z.number().int().optional().nullable(),
  i2: z.number().int().optional().nullable(),
  i3: z.number().int().optional().nullable(),
  i4: z.number().int().optional().nullable(),
  i5: z.number().int().optional().nullable(),
  i6: z.number().int().optional().nullable(),
  i7: z.number().int().optional().nullable(),
  i8: z.number().int().optional().nullable(),
  s2: z.string().optional().nullable()
}).strict();

export const EventsV9UncheckedCreateInputSchema: z.ZodType<Prisma.EventsV9UncheckedCreateInput> = z.object({
  cid: z.number().int().optional().nullable(),
  id: z.number().int(),
  altm: z.number().int().optional().nullable(),
  nid: z.number().int().optional().nullable(),
  ttl: z.string().optional().nullable(),
  s1: z.string().optional().nullable(),
  estart: z.number().int().optional().nullable(),
  eend: z.number().int().optional().nullable(),
  istart: z.number().int(),
  iend: z.number().int().optional().nullable(),
  loc: z.string().optional().nullable(),
  snz: z.number().int().optional().nullable(),
  ls: z.number().int().optional().nullable(),
  dsts: z.number().int().optional().nullable(),
  clr: z.number().int().optional().nullable(),
  rep: z.number().int().optional().nullable(),
  alld: z.number().int().optional().nullable(),
  ogn: z.number().int().optional().nullable(),
  fsn: z.number().int().optional().nullable(),
  attsts: z.number().int().optional().nullable(),
  oattsts: z.number().int().optional().nullable(),
  i1: z.number().int().optional().nullable(),
  i2: z.number().int().optional().nullable(),
  i3: z.number().int().optional().nullable(),
  i4: z.number().int().optional().nullable(),
  i5: z.number().int().optional().nullable(),
  i6: z.number().int().optional().nullable(),
  i7: z.number().int().optional().nullable(),
  i8: z.number().int().optional().nullable(),
  s2: z.string().optional().nullable()
}).strict();

export const EventsV9UpdateInputSchema: z.ZodType<Prisma.EventsV9UpdateInput> = z.object({
  cid: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  id: z.union([ z.number().int(),z.lazy(() => IntFieldUpdateOperationsInputSchema) ]).optional(),
  altm: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  nid: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  ttl: z.union([ z.string(),z.lazy(() => NullableStringFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  s1: z.union([ z.string(),z.lazy(() => NullableStringFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  estart: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  eend: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  istart: z.union([ z.number().int(),z.lazy(() => IntFieldUpdateOperationsInputSchema) ]).optional(),
  iend: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  loc: z.union([ z.string(),z.lazy(() => NullableStringFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  snz: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  ls: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  dsts: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  clr: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  rep: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  alld: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  ogn: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  fsn: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  attsts: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  oattsts: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  i1: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  i2: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  i3: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  i4: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  i5: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  i6: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  i7: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  i8: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  s2: z.union([ z.string(),z.lazy(() => NullableStringFieldUpdateOperationsInputSchema) ]).optional().nullable(),
}).strict();

export const EventsV9UncheckedUpdateInputSchema: z.ZodType<Prisma.EventsV9UncheckedUpdateInput> = z.object({
  cid: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  id: z.union([ z.number().int(),z.lazy(() => IntFieldUpdateOperationsInputSchema) ]).optional(),
  altm: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  nid: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  ttl: z.union([ z.string(),z.lazy(() => NullableStringFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  s1: z.union([ z.string(),z.lazy(() => NullableStringFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  estart: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  eend: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  istart: z.union([ z.number().int(),z.lazy(() => IntFieldUpdateOperationsInputSchema) ]).optional(),
  iend: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  loc: z.union([ z.string(),z.lazy(() => NullableStringFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  snz: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  ls: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  dsts: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  clr: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  rep: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  alld: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  ogn: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  fsn: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  attsts: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  oattsts: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  i1: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  i2: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  i3: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  i4: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  i5: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  i6: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  i7: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  i8: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  s2: z.union([ z.string(),z.lazy(() => NullableStringFieldUpdateOperationsInputSchema) ]).optional().nullable(),
}).strict();

export const EventsV9CreateManyInputSchema: z.ZodType<Prisma.EventsV9CreateManyInput> = z.object({
  cid: z.number().int().optional().nullable(),
  id: z.number().int(),
  altm: z.number().int().optional().nullable(),
  nid: z.number().int().optional().nullable(),
  ttl: z.string().optional().nullable(),
  s1: z.string().optional().nullable(),
  estart: z.number().int().optional().nullable(),
  eend: z.number().int().optional().nullable(),
  istart: z.number().int(),
  iend: z.number().int().optional().nullable(),
  loc: z.string().optional().nullable(),
  snz: z.number().int().optional().nullable(),
  ls: z.number().int().optional().nullable(),
  dsts: z.number().int().optional().nullable(),
  clr: z.number().int().optional().nullable(),
  rep: z.number().int().optional().nullable(),
  alld: z.number().int().optional().nullable(),
  ogn: z.number().int().optional().nullable(),
  fsn: z.number().int().optional().nullable(),
  attsts: z.number().int().optional().nullable(),
  oattsts: z.number().int().optional().nullable(),
  i1: z.number().int().optional().nullable(),
  i2: z.number().int().optional().nullable(),
  i3: z.number().int().optional().nullable(),
  i4: z.number().int().optional().nullable(),
  i5: z.number().int().optional().nullable(),
  i6: z.number().int().optional().nullable(),
  i7: z.number().int().optional().nullable(),
  i8: z.number().int().optional().nullable(),
  s2: z.string().optional().nullable()
}).strict();

export const EventsV9UpdateManyMutationInputSchema: z.ZodType<Prisma.EventsV9UpdateManyMutationInput> = z.object({
  cid: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  id: z.union([ z.number().int(),z.lazy(() => IntFieldUpdateOperationsInputSchema) ]).optional(),
  altm: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  nid: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  ttl: z.union([ z.string(),z.lazy(() => NullableStringFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  s1: z.union([ z.string(),z.lazy(() => NullableStringFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  estart: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  eend: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  istart: z.union([ z.number().int(),z.lazy(() => IntFieldUpdateOperationsInputSchema) ]).optional(),
  iend: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  loc: z.union([ z.string(),z.lazy(() => NullableStringFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  snz: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  ls: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  dsts: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  clr: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  rep: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  alld: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  ogn: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  fsn: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  attsts: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  oattsts: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  i1: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  i2: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  i3: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  i4: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  i5: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  i6: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  i7: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  i8: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  s2: z.union([ z.string(),z.lazy(() => NullableStringFieldUpdateOperationsInputSchema) ]).optional().nullable(),
}).strict();

export const EventsV9UncheckedUpdateManyInputSchema: z.ZodType<Prisma.EventsV9UncheckedUpdateManyInput> = z.object({
  cid: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  id: z.union([ z.number().int(),z.lazy(() => IntFieldUpdateOperationsInputSchema) ]).optional(),
  altm: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  nid: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  ttl: z.union([ z.string(),z.lazy(() => NullableStringFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  s1: z.union([ z.string(),z.lazy(() => NullableStringFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  estart: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  eend: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  istart: z.union([ z.number().int(),z.lazy(() => IntFieldUpdateOperationsInputSchema) ]).optional(),
  iend: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  loc: z.union([ z.string(),z.lazy(() => NullableStringFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  snz: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  ls: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  dsts: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  clr: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  rep: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  alld: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  ogn: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  fsn: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  attsts: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  oattsts: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  i1: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  i2: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  i3: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  i4: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  i5: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  i6: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  i7: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  i8: z.union([ z.number().int(),z.lazy(() => NullableIntFieldUpdateOperationsInputSchema) ]).optional().nullable(),
  s2: z.union([ z.string(),z.lazy(() => NullableStringFieldUpdateOperationsInputSchema) ]).optional().nullable(),
}).strict();

export const ItemsCreateInputSchema: z.ZodType<Prisma.ItemsCreateInput> = z.object({
  value: z.string()
}).strict();

export const ItemsUncheckedCreateInputSchema: z.ZodType<Prisma.ItemsUncheckedCreateInput> = z.object({
  value: z.string()
}).strict();

export const ItemsUpdateInputSchema: z.ZodType<Prisma.ItemsUpdateInput> = z.object({
  value: z.union([ z.string(),z.lazy(() => StringFieldUpdateOperationsInputSchema) ]).optional(),
}).strict();

export const ItemsUncheckedUpdateInputSchema: z.ZodType<Prisma.ItemsUncheckedUpdateInput> = z.object({
  value: z.union([ z.string(),z.lazy(() => StringFieldUpdateOperationsInputSchema) ]).optional(),
}).strict();

export const ItemsCreateManyInputSchema: z.ZodType<Prisma.ItemsCreateManyInput> = z.object({
  value: z.string()
}).strict();

export const ItemsUpdateManyMutationInputSchema: z.ZodType<Prisma.ItemsUpdateManyMutationInput> = z.object({
  value: z.union([ z.string(),z.lazy(() => StringFieldUpdateOperationsInputSchema) ]).optional(),
}).strict();

export const ItemsUncheckedUpdateManyInputSchema: z.ZodType<Prisma.ItemsUncheckedUpdateManyInput> = z.object({
  value: z.union([ z.string(),z.lazy(() => StringFieldUpdateOperationsInputSchema) ]).optional(),
}).strict();

export const IntNullableFilterSchema: z.ZodType<Prisma.IntNullableFilter> = z.object({
  equals: z.number().optional().nullable(),
  in: z.number().array().optional().nullable(),
  notIn: z.number().array().optional().nullable(),
  lt: z.number().optional(),
  lte: z.number().optional(),
  gt: z.number().optional(),
  gte: z.number().optional(),
  not: z.union([ z.number(),z.lazy(() => NestedIntNullableFilterSchema) ]).optional().nullable(),
}).strict();

export const IntFilterSchema: z.ZodType<Prisma.IntFilter> = z.object({
  equals: z.number().optional(),
  in: z.number().array().optional(),
  notIn: z.number().array().optional(),
  lt: z.number().optional(),
  lte: z.number().optional(),
  gt: z.number().optional(),
  gte: z.number().optional(),
  not: z.union([ z.number(),z.lazy(() => NestedIntFilterSchema) ]).optional(),
}).strict();

export const StringNullableFilterSchema: z.ZodType<Prisma.StringNullableFilter> = z.object({
  equals: z.string().optional().nullable(),
  in: z.string().array().optional().nullable(),
  notIn: z.string().array().optional().nullable(),
  lt: z.string().optional(),
  lte: z.string().optional(),
  gt: z.string().optional(),
  gte: z.string().optional(),
  contains: z.string().optional(),
  startsWith: z.string().optional(),
  endsWith: z.string().optional(),
  mode: z.lazy(() => QueryModeSchema).optional(),
  not: z.union([ z.string(),z.lazy(() => NestedStringNullableFilterSchema) ]).optional().nullable(),
}).strict();

export const EventsV9IdIstartCompoundUniqueInputSchema: z.ZodType<Prisma.EventsV9IdIstartCompoundUniqueInput> = z.object({
  id: z.number(),
  istart: z.number()
}).strict();

export const EventsV9CountOrderByAggregateInputSchema: z.ZodType<Prisma.EventsV9CountOrderByAggregateInput> = z.object({
  cid: z.lazy(() => SortOrderSchema).optional(),
  id: z.lazy(() => SortOrderSchema).optional(),
  altm: z.lazy(() => SortOrderSchema).optional(),
  nid: z.lazy(() => SortOrderSchema).optional(),
  ttl: z.lazy(() => SortOrderSchema).optional(),
  s1: z.lazy(() => SortOrderSchema).optional(),
  estart: z.lazy(() => SortOrderSchema).optional(),
  eend: z.lazy(() => SortOrderSchema).optional(),
  istart: z.lazy(() => SortOrderSchema).optional(),
  iend: z.lazy(() => SortOrderSchema).optional(),
  loc: z.lazy(() => SortOrderSchema).optional(),
  snz: z.lazy(() => SortOrderSchema).optional(),
  ls: z.lazy(() => SortOrderSchema).optional(),
  dsts: z.lazy(() => SortOrderSchema).optional(),
  clr: z.lazy(() => SortOrderSchema).optional(),
  rep: z.lazy(() => SortOrderSchema).optional(),
  alld: z.lazy(() => SortOrderSchema).optional(),
  ogn: z.lazy(() => SortOrderSchema).optional(),
  fsn: z.lazy(() => SortOrderSchema).optional(),
  attsts: z.lazy(() => SortOrderSchema).optional(),
  oattsts: z.lazy(() => SortOrderSchema).optional(),
  i1: z.lazy(() => SortOrderSchema).optional(),
  i2: z.lazy(() => SortOrderSchema).optional(),
  i3: z.lazy(() => SortOrderSchema).optional(),
  i4: z.lazy(() => SortOrderSchema).optional(),
  i5: z.lazy(() => SortOrderSchema).optional(),
  i6: z.lazy(() => SortOrderSchema).optional(),
  i7: z.lazy(() => SortOrderSchema).optional(),
  i8: z.lazy(() => SortOrderSchema).optional(),
  s2: z.lazy(() => SortOrderSchema).optional()
}).strict();

export const EventsV9AvgOrderByAggregateInputSchema: z.ZodType<Prisma.EventsV9AvgOrderByAggregateInput> = z.object({
  cid: z.lazy(() => SortOrderSchema).optional(),
  id: z.lazy(() => SortOrderSchema).optional(),
  altm: z.lazy(() => SortOrderSchema).optional(),
  nid: z.lazy(() => SortOrderSchema).optional(),
  estart: z.lazy(() => SortOrderSchema).optional(),
  eend: z.lazy(() => SortOrderSchema).optional(),
  istart: z.lazy(() => SortOrderSchema).optional(),
  iend: z.lazy(() => SortOrderSchema).optional(),
  snz: z.lazy(() => SortOrderSchema).optional(),
  ls: z.lazy(() => SortOrderSchema).optional(),
  dsts: z.lazy(() => SortOrderSchema).optional(),
  clr: z.lazy(() => SortOrderSchema).optional(),
  rep: z.lazy(() => SortOrderSchema).optional(),
  alld: z.lazy(() => SortOrderSchema).optional(),
  ogn: z.lazy(() => SortOrderSchema).optional(),
  fsn: z.lazy(() => SortOrderSchema).optional(),
  attsts: z.lazy(() => SortOrderSchema).optional(),
  oattsts: z.lazy(() => SortOrderSchema).optional(),
  i1: z.lazy(() => SortOrderSchema).optional(),
  i2: z.lazy(() => SortOrderSchema).optional(),
  i3: z.lazy(() => SortOrderSchema).optional(),
  i4: z.lazy(() => SortOrderSchema).optional(),
  i5: z.lazy(() => SortOrderSchema).optional(),
  i6: z.lazy(() => SortOrderSchema).optional(),
  i7: z.lazy(() => SortOrderSchema).optional(),
  i8: z.lazy(() => SortOrderSchema).optional()
}).strict();

export const EventsV9MaxOrderByAggregateInputSchema: z.ZodType<Prisma.EventsV9MaxOrderByAggregateInput> = z.object({
  cid: z.lazy(() => SortOrderSchema).optional(),
  id: z.lazy(() => SortOrderSchema).optional(),
  altm: z.lazy(() => SortOrderSchema).optional(),
  nid: z.lazy(() => SortOrderSchema).optional(),
  ttl: z.lazy(() => SortOrderSchema).optional(),
  s1: z.lazy(() => SortOrderSchema).optional(),
  estart: z.lazy(() => SortOrderSchema).optional(),
  eend: z.lazy(() => SortOrderSchema).optional(),
  istart: z.lazy(() => SortOrderSchema).optional(),
  iend: z.lazy(() => SortOrderSchema).optional(),
  loc: z.lazy(() => SortOrderSchema).optional(),
  snz: z.lazy(() => SortOrderSchema).optional(),
  ls: z.lazy(() => SortOrderSchema).optional(),
  dsts: z.lazy(() => SortOrderSchema).optional(),
  clr: z.lazy(() => SortOrderSchema).optional(),
  rep: z.lazy(() => SortOrderSchema).optional(),
  alld: z.lazy(() => SortOrderSchema).optional(),
  ogn: z.lazy(() => SortOrderSchema).optional(),
  fsn: z.lazy(() => SortOrderSchema).optional(),
  attsts: z.lazy(() => SortOrderSchema).optional(),
  oattsts: z.lazy(() => SortOrderSchema).optional(),
  i1: z.lazy(() => SortOrderSchema).optional(),
  i2: z.lazy(() => SortOrderSchema).optional(),
  i3: z.lazy(() => SortOrderSchema).optional(),
  i4: z.lazy(() => SortOrderSchema).optional(),
  i5: z.lazy(() => SortOrderSchema).optional(),
  i6: z.lazy(() => SortOrderSchema).optional(),
  i7: z.lazy(() => SortOrderSchema).optional(),
  i8: z.lazy(() => SortOrderSchema).optional(),
  s2: z.lazy(() => SortOrderSchema).optional()
}).strict();

export const EventsV9MinOrderByAggregateInputSchema: z.ZodType<Prisma.EventsV9MinOrderByAggregateInput> = z.object({
  cid: z.lazy(() => SortOrderSchema).optional(),
  id: z.lazy(() => SortOrderSchema).optional(),
  altm: z.lazy(() => SortOrderSchema).optional(),
  nid: z.lazy(() => SortOrderSchema).optional(),
  ttl: z.lazy(() => SortOrderSchema).optional(),
  s1: z.lazy(() => SortOrderSchema).optional(),
  estart: z.lazy(() => SortOrderSchema).optional(),
  eend: z.lazy(() => SortOrderSchema).optional(),
  istart: z.lazy(() => SortOrderSchema).optional(),
  iend: z.lazy(() => SortOrderSchema).optional(),
  loc: z.lazy(() => SortOrderSchema).optional(),
  snz: z.lazy(() => SortOrderSchema).optional(),
  ls: z.lazy(() => SortOrderSchema).optional(),
  dsts: z.lazy(() => SortOrderSchema).optional(),
  clr: z.lazy(() => SortOrderSchema).optional(),
  rep: z.lazy(() => SortOrderSchema).optional(),
  alld: z.lazy(() => SortOrderSchema).optional(),
  ogn: z.lazy(() => SortOrderSchema).optional(),
  fsn: z.lazy(() => SortOrderSchema).optional(),
  attsts: z.lazy(() => SortOrderSchema).optional(),
  oattsts: z.lazy(() => SortOrderSchema).optional(),
  i1: z.lazy(() => SortOrderSchema).optional(),
  i2: z.lazy(() => SortOrderSchema).optional(),
  i3: z.lazy(() => SortOrderSchema).optional(),
  i4: z.lazy(() => SortOrderSchema).optional(),
  i5: z.lazy(() => SortOrderSchema).optional(),
  i6: z.lazy(() => SortOrderSchema).optional(),
  i7: z.lazy(() => SortOrderSchema).optional(),
  i8: z.lazy(() => SortOrderSchema).optional(),
  s2: z.lazy(() => SortOrderSchema).optional()
}).strict();

export const EventsV9SumOrderByAggregateInputSchema: z.ZodType<Prisma.EventsV9SumOrderByAggregateInput> = z.object({
  cid: z.lazy(() => SortOrderSchema).optional(),
  id: z.lazy(() => SortOrderSchema).optional(),
  altm: z.lazy(() => SortOrderSchema).optional(),
  nid: z.lazy(() => SortOrderSchema).optional(),
  estart: z.lazy(() => SortOrderSchema).optional(),
  eend: z.lazy(() => SortOrderSchema).optional(),
  istart: z.lazy(() => SortOrderSchema).optional(),
  iend: z.lazy(() => SortOrderSchema).optional(),
  snz: z.lazy(() => SortOrderSchema).optional(),
  ls: z.lazy(() => SortOrderSchema).optional(),
  dsts: z.lazy(() => SortOrderSchema).optional(),
  clr: z.lazy(() => SortOrderSchema).optional(),
  rep: z.lazy(() => SortOrderSchema).optional(),
  alld: z.lazy(() => SortOrderSchema).optional(),
  ogn: z.lazy(() => SortOrderSchema).optional(),
  fsn: z.lazy(() => SortOrderSchema).optional(),
  attsts: z.lazy(() => SortOrderSchema).optional(),
  oattsts: z.lazy(() => SortOrderSchema).optional(),
  i1: z.lazy(() => SortOrderSchema).optional(),
  i2: z.lazy(() => SortOrderSchema).optional(),
  i3: z.lazy(() => SortOrderSchema).optional(),
  i4: z.lazy(() => SortOrderSchema).optional(),
  i5: z.lazy(() => SortOrderSchema).optional(),
  i6: z.lazy(() => SortOrderSchema).optional(),
  i7: z.lazy(() => SortOrderSchema).optional(),
  i8: z.lazy(() => SortOrderSchema).optional()
}).strict();

export const IntNullableWithAggregatesFilterSchema: z.ZodType<Prisma.IntNullableWithAggregatesFilter> = z.object({
  equals: z.number().optional().nullable(),
  in: z.number().array().optional().nullable(),
  notIn: z.number().array().optional().nullable(),
  lt: z.number().optional(),
  lte: z.number().optional(),
  gt: z.number().optional(),
  gte: z.number().optional(),
  not: z.union([ z.number(),z.lazy(() => NestedIntNullableWithAggregatesFilterSchema) ]).optional().nullable(),
  _count: z.lazy(() => NestedIntNullableFilterSchema).optional(),
  _avg: z.lazy(() => NestedFloatNullableFilterSchema).optional(),
  _sum: z.lazy(() => NestedIntNullableFilterSchema).optional(),
  _min: z.lazy(() => NestedIntNullableFilterSchema).optional(),
  _max: z.lazy(() => NestedIntNullableFilterSchema).optional()
}).strict();

export const IntWithAggregatesFilterSchema: z.ZodType<Prisma.IntWithAggregatesFilter> = z.object({
  equals: z.number().optional(),
  in: z.number().array().optional(),
  notIn: z.number().array().optional(),
  lt: z.number().optional(),
  lte: z.number().optional(),
  gt: z.number().optional(),
  gte: z.number().optional(),
  not: z.union([ z.number(),z.lazy(() => NestedIntWithAggregatesFilterSchema) ]).optional(),
  _count: z.lazy(() => NestedIntFilterSchema).optional(),
  _avg: z.lazy(() => NestedFloatFilterSchema).optional(),
  _sum: z.lazy(() => NestedIntFilterSchema).optional(),
  _min: z.lazy(() => NestedIntFilterSchema).optional(),
  _max: z.lazy(() => NestedIntFilterSchema).optional()
}).strict();

export const StringNullableWithAggregatesFilterSchema: z.ZodType<Prisma.StringNullableWithAggregatesFilter> = z.object({
  equals: z.string().optional().nullable(),
  in: z.string().array().optional().nullable(),
  notIn: z.string().array().optional().nullable(),
  lt: z.string().optional(),
  lte: z.string().optional(),
  gt: z.string().optional(),
  gte: z.string().optional(),
  contains: z.string().optional(),
  startsWith: z.string().optional(),
  endsWith: z.string().optional(),
  mode: z.lazy(() => QueryModeSchema).optional(),
  not: z.union([ z.string(),z.lazy(() => NestedStringNullableWithAggregatesFilterSchema) ]).optional().nullable(),
  _count: z.lazy(() => NestedIntNullableFilterSchema).optional(),
  _min: z.lazy(() => NestedStringNullableFilterSchema).optional(),
  _max: z.lazy(() => NestedStringNullableFilterSchema).optional()
}).strict();

export const StringFilterSchema: z.ZodType<Prisma.StringFilter> = z.object({
  equals: z.string().optional(),
  in: z.string().array().optional(),
  notIn: z.string().array().optional(),
  lt: z.string().optional(),
  lte: z.string().optional(),
  gt: z.string().optional(),
  gte: z.string().optional(),
  contains: z.string().optional(),
  startsWith: z.string().optional(),
  endsWith: z.string().optional(),
  mode: z.lazy(() => QueryModeSchema).optional(),
  not: z.union([ z.string(),z.lazy(() => NestedStringFilterSchema) ]).optional(),
}).strict();

export const ItemsCountOrderByAggregateInputSchema: z.ZodType<Prisma.ItemsCountOrderByAggregateInput> = z.object({
  value: z.lazy(() => SortOrderSchema).optional()
}).strict();

export const ItemsMaxOrderByAggregateInputSchema: z.ZodType<Prisma.ItemsMaxOrderByAggregateInput> = z.object({
  value: z.lazy(() => SortOrderSchema).optional()
}).strict();

export const ItemsMinOrderByAggregateInputSchema: z.ZodType<Prisma.ItemsMinOrderByAggregateInput> = z.object({
  value: z.lazy(() => SortOrderSchema).optional()
}).strict();

export const StringWithAggregatesFilterSchema: z.ZodType<Prisma.StringWithAggregatesFilter> = z.object({
  equals: z.string().optional(),
  in: z.string().array().optional(),
  notIn: z.string().array().optional(),
  lt: z.string().optional(),
  lte: z.string().optional(),
  gt: z.string().optional(),
  gte: z.string().optional(),
  contains: z.string().optional(),
  startsWith: z.string().optional(),
  endsWith: z.string().optional(),
  mode: z.lazy(() => QueryModeSchema).optional(),
  not: z.union([ z.string(),z.lazy(() => NestedStringWithAggregatesFilterSchema) ]).optional(),
  _count: z.lazy(() => NestedIntFilterSchema).optional(),
  _min: z.lazy(() => NestedStringFilterSchema).optional(),
  _max: z.lazy(() => NestedStringFilterSchema).optional()
}).strict();

export const NullableIntFieldUpdateOperationsInputSchema: z.ZodType<Prisma.NullableIntFieldUpdateOperationsInput> = z.object({
  set: z.number().optional().nullable(),
  increment: z.number().optional(),
  decrement: z.number().optional(),
  multiply: z.number().optional(),
  divide: z.number().optional()
}).strict();

export const IntFieldUpdateOperationsInputSchema: z.ZodType<Prisma.IntFieldUpdateOperationsInput> = z.object({
  set: z.number().optional(),
  increment: z.number().optional(),
  decrement: z.number().optional(),
  multiply: z.number().optional(),
  divide: z.number().optional()
}).strict();

export const NullableStringFieldUpdateOperationsInputSchema: z.ZodType<Prisma.NullableStringFieldUpdateOperationsInput> = z.object({
  set: z.string().optional().nullable()
}).strict();

export const StringFieldUpdateOperationsInputSchema: z.ZodType<Prisma.StringFieldUpdateOperationsInput> = z.object({
  set: z.string().optional()
}).strict();

export const NestedIntNullableFilterSchema: z.ZodType<Prisma.NestedIntNullableFilter> = z.object({
  equals: z.number().optional().nullable(),
  in: z.number().array().optional().nullable(),
  notIn: z.number().array().optional().nullable(),
  lt: z.number().optional(),
  lte: z.number().optional(),
  gt: z.number().optional(),
  gte: z.number().optional(),
  not: z.union([ z.number(),z.lazy(() => NestedIntNullableFilterSchema) ]).optional().nullable(),
}).strict();

export const NestedIntFilterSchema: z.ZodType<Prisma.NestedIntFilter> = z.object({
  equals: z.number().optional(),
  in: z.number().array().optional(),
  notIn: z.number().array().optional(),
  lt: z.number().optional(),
  lte: z.number().optional(),
  gt: z.number().optional(),
  gte: z.number().optional(),
  not: z.union([ z.number(),z.lazy(() => NestedIntFilterSchema) ]).optional(),
}).strict();

export const NestedStringNullableFilterSchema: z.ZodType<Prisma.NestedStringNullableFilter> = z.object({
  equals: z.string().optional().nullable(),
  in: z.string().array().optional().nullable(),
  notIn: z.string().array().optional().nullable(),
  lt: z.string().optional(),
  lte: z.string().optional(),
  gt: z.string().optional(),
  gte: z.string().optional(),
  contains: z.string().optional(),
  startsWith: z.string().optional(),
  endsWith: z.string().optional(),
  not: z.union([ z.string(),z.lazy(() => NestedStringNullableFilterSchema) ]).optional().nullable(),
}).strict();

export const NestedIntNullableWithAggregatesFilterSchema: z.ZodType<Prisma.NestedIntNullableWithAggregatesFilter> = z.object({
  equals: z.number().optional().nullable(),
  in: z.number().array().optional().nullable(),
  notIn: z.number().array().optional().nullable(),
  lt: z.number().optional(),
  lte: z.number().optional(),
  gt: z.number().optional(),
  gte: z.number().optional(),
  not: z.union([ z.number(),z.lazy(() => NestedIntNullableWithAggregatesFilterSchema) ]).optional().nullable(),
  _count: z.lazy(() => NestedIntNullableFilterSchema).optional(),
  _avg: z.lazy(() => NestedFloatNullableFilterSchema).optional(),
  _sum: z.lazy(() => NestedIntNullableFilterSchema).optional(),
  _min: z.lazy(() => NestedIntNullableFilterSchema).optional(),
  _max: z.lazy(() => NestedIntNullableFilterSchema).optional()
}).strict();

export const NestedFloatNullableFilterSchema: z.ZodType<Prisma.NestedFloatNullableFilter> = z.object({
  equals: z.number().optional().nullable(),
  in: z.number().array().optional().nullable(),
  notIn: z.number().array().optional().nullable(),
  lt: z.number().optional(),
  lte: z.number().optional(),
  gt: z.number().optional(),
  gte: z.number().optional(),
  not: z.union([ z.number(),z.lazy(() => NestedFloatNullableFilterSchema) ]).optional().nullable(),
}).strict();

export const NestedIntWithAggregatesFilterSchema: z.ZodType<Prisma.NestedIntWithAggregatesFilter> = z.object({
  equals: z.number().optional(),
  in: z.number().array().optional(),
  notIn: z.number().array().optional(),
  lt: z.number().optional(),
  lte: z.number().optional(),
  gt: z.number().optional(),
  gte: z.number().optional(),
  not: z.union([ z.number(),z.lazy(() => NestedIntWithAggregatesFilterSchema) ]).optional(),
  _count: z.lazy(() => NestedIntFilterSchema).optional(),
  _avg: z.lazy(() => NestedFloatFilterSchema).optional(),
  _sum: z.lazy(() => NestedIntFilterSchema).optional(),
  _min: z.lazy(() => NestedIntFilterSchema).optional(),
  _max: z.lazy(() => NestedIntFilterSchema).optional()
}).strict();

export const NestedFloatFilterSchema: z.ZodType<Prisma.NestedFloatFilter> = z.object({
  equals: z.number().optional(),
  in: z.number().array().optional(),
  notIn: z.number().array().optional(),
  lt: z.number().optional(),
  lte: z.number().optional(),
  gt: z.number().optional(),
  gte: z.number().optional(),
  not: z.union([ z.number(),z.lazy(() => NestedFloatFilterSchema) ]).optional(),
}).strict();

export const NestedStringNullableWithAggregatesFilterSchema: z.ZodType<Prisma.NestedStringNullableWithAggregatesFilter> = z.object({
  equals: z.string().optional().nullable(),
  in: z.string().array().optional().nullable(),
  notIn: z.string().array().optional().nullable(),
  lt: z.string().optional(),
  lte: z.string().optional(),
  gt: z.string().optional(),
  gte: z.string().optional(),
  contains: z.string().optional(),
  startsWith: z.string().optional(),
  endsWith: z.string().optional(),
  not: z.union([ z.string(),z.lazy(() => NestedStringNullableWithAggregatesFilterSchema) ]).optional().nullable(),
  _count: z.lazy(() => NestedIntNullableFilterSchema).optional(),
  _min: z.lazy(() => NestedStringNullableFilterSchema).optional(),
  _max: z.lazy(() => NestedStringNullableFilterSchema).optional()
}).strict();

export const NestedStringFilterSchema: z.ZodType<Prisma.NestedStringFilter> = z.object({
  equals: z.string().optional(),
  in: z.string().array().optional(),
  notIn: z.string().array().optional(),
  lt: z.string().optional(),
  lte: z.string().optional(),
  gt: z.string().optional(),
  gte: z.string().optional(),
  contains: z.string().optional(),
  startsWith: z.string().optional(),
  endsWith: z.string().optional(),
  not: z.union([ z.string(),z.lazy(() => NestedStringFilterSchema) ]).optional(),
}).strict();

export const NestedStringWithAggregatesFilterSchema: z.ZodType<Prisma.NestedStringWithAggregatesFilter> = z.object({
  equals: z.string().optional(),
  in: z.string().array().optional(),
  notIn: z.string().array().optional(),
  lt: z.string().optional(),
  lte: z.string().optional(),
  gt: z.string().optional(),
  gte: z.string().optional(),
  contains: z.string().optional(),
  startsWith: z.string().optional(),
  endsWith: z.string().optional(),
  not: z.union([ z.string(),z.lazy(() => NestedStringWithAggregatesFilterSchema) ]).optional(),
  _count: z.lazy(() => NestedIntFilterSchema).optional(),
  _min: z.lazy(() => NestedStringFilterSchema).optional(),
  _max: z.lazy(() => NestedStringFilterSchema).optional()
}).strict();

/////////////////////////////////////////
// ARGS
/////////////////////////////////////////

export const EventsV9FindFirstArgsSchema: z.ZodType<Prisma.EventsV9FindFirstArgs> = z.object({
  select: EventsV9SelectSchema.optional(),
  where: EventsV9WhereInputSchema.optional(),
  orderBy: z.union([ EventsV9OrderByWithRelationInputSchema.array(),EventsV9OrderByWithRelationInputSchema ]).optional(),
  cursor: EventsV9WhereUniqueInputSchema.optional(),
  take: z.number().optional(),
  skip: z.number().optional(),
  distinct: EventsV9ScalarFieldEnumSchema.array().optional(),
}).strict()

export const EventsV9FindFirstOrThrowArgsSchema: z.ZodType<Prisma.EventsV9FindFirstOrThrowArgs> = z.object({
  select: EventsV9SelectSchema.optional(),
  where: EventsV9WhereInputSchema.optional(),
  orderBy: z.union([ EventsV9OrderByWithRelationInputSchema.array(),EventsV9OrderByWithRelationInputSchema ]).optional(),
  cursor: EventsV9WhereUniqueInputSchema.optional(),
  take: z.number().optional(),
  skip: z.number().optional(),
  distinct: EventsV9ScalarFieldEnumSchema.array().optional(),
}).strict()

export const EventsV9FindManyArgsSchema: z.ZodType<Prisma.EventsV9FindManyArgs> = z.object({
  select: EventsV9SelectSchema.optional(),
  where: EventsV9WhereInputSchema.optional(),
  orderBy: z.union([ EventsV9OrderByWithRelationInputSchema.array(),EventsV9OrderByWithRelationInputSchema ]).optional(),
  cursor: EventsV9WhereUniqueInputSchema.optional(),
  take: z.number().optional(),
  skip: z.number().optional(),
  distinct: EventsV9ScalarFieldEnumSchema.array().optional(),
}).strict()

export const EventsV9AggregateArgsSchema: z.ZodType<Prisma.EventsV9AggregateArgs> = z.object({
  where: EventsV9WhereInputSchema.optional(),
  orderBy: z.union([ EventsV9OrderByWithRelationInputSchema.array(),EventsV9OrderByWithRelationInputSchema ]).optional(),
  cursor: EventsV9WhereUniqueInputSchema.optional(),
  take: z.number().optional(),
  skip: z.number().optional(),
}).strict()

export const EventsV9GroupByArgsSchema: z.ZodType<Prisma.EventsV9GroupByArgs> = z.object({
  where: EventsV9WhereInputSchema.optional(),
  orderBy: z.union([ EventsV9OrderByWithAggregationInputSchema.array(),EventsV9OrderByWithAggregationInputSchema ]).optional(),
  by: EventsV9ScalarFieldEnumSchema.array(),
  having: EventsV9ScalarWhereWithAggregatesInputSchema.optional(),
  take: z.number().optional(),
  skip: z.number().optional(),
}).strict()

export const EventsV9FindUniqueArgsSchema: z.ZodType<Prisma.EventsV9FindUniqueArgs> = z.object({
  select: EventsV9SelectSchema.optional(),
  where: EventsV9WhereUniqueInputSchema,
}).strict()

export const EventsV9FindUniqueOrThrowArgsSchema: z.ZodType<Prisma.EventsV9FindUniqueOrThrowArgs> = z.object({
  select: EventsV9SelectSchema.optional(),
  where: EventsV9WhereUniqueInputSchema,
}).strict()

export const ItemsFindFirstArgsSchema: z.ZodType<Prisma.ItemsFindFirstArgs> = z.object({
  select: ItemsSelectSchema.optional(),
  where: ItemsWhereInputSchema.optional(),
  orderBy: z.union([ ItemsOrderByWithRelationInputSchema.array(),ItemsOrderByWithRelationInputSchema ]).optional(),
  cursor: ItemsWhereUniqueInputSchema.optional(),
  take: z.number().optional(),
  skip: z.number().optional(),
  distinct: ItemsScalarFieldEnumSchema.array().optional(),
}).strict()

export const ItemsFindFirstOrThrowArgsSchema: z.ZodType<Prisma.ItemsFindFirstOrThrowArgs> = z.object({
  select: ItemsSelectSchema.optional(),
  where: ItemsWhereInputSchema.optional(),
  orderBy: z.union([ ItemsOrderByWithRelationInputSchema.array(),ItemsOrderByWithRelationInputSchema ]).optional(),
  cursor: ItemsWhereUniqueInputSchema.optional(),
  take: z.number().optional(),
  skip: z.number().optional(),
  distinct: ItemsScalarFieldEnumSchema.array().optional(),
}).strict()

export const ItemsFindManyArgsSchema: z.ZodType<Prisma.ItemsFindManyArgs> = z.object({
  select: ItemsSelectSchema.optional(),
  where: ItemsWhereInputSchema.optional(),
  orderBy: z.union([ ItemsOrderByWithRelationInputSchema.array(),ItemsOrderByWithRelationInputSchema ]).optional(),
  cursor: ItemsWhereUniqueInputSchema.optional(),
  take: z.number().optional(),
  skip: z.number().optional(),
  distinct: ItemsScalarFieldEnumSchema.array().optional(),
}).strict()

export const ItemsAggregateArgsSchema: z.ZodType<Prisma.ItemsAggregateArgs> = z.object({
  where: ItemsWhereInputSchema.optional(),
  orderBy: z.union([ ItemsOrderByWithRelationInputSchema.array(),ItemsOrderByWithRelationInputSchema ]).optional(),
  cursor: ItemsWhereUniqueInputSchema.optional(),
  take: z.number().optional(),
  skip: z.number().optional(),
}).strict()

export const ItemsGroupByArgsSchema: z.ZodType<Prisma.ItemsGroupByArgs> = z.object({
  where: ItemsWhereInputSchema.optional(),
  orderBy: z.union([ ItemsOrderByWithAggregationInputSchema.array(),ItemsOrderByWithAggregationInputSchema ]).optional(),
  by: ItemsScalarFieldEnumSchema.array(),
  having: ItemsScalarWhereWithAggregatesInputSchema.optional(),
  take: z.number().optional(),
  skip: z.number().optional(),
}).strict()

export const ItemsFindUniqueArgsSchema: z.ZodType<Prisma.ItemsFindUniqueArgs> = z.object({
  select: ItemsSelectSchema.optional(),
  where: ItemsWhereUniqueInputSchema,
}).strict()

export const ItemsFindUniqueOrThrowArgsSchema: z.ZodType<Prisma.ItemsFindUniqueOrThrowArgs> = z.object({
  select: ItemsSelectSchema.optional(),
  where: ItemsWhereUniqueInputSchema,
}).strict()

export const EventsV9CreateArgsSchema: z.ZodType<Prisma.EventsV9CreateArgs> = z.object({
  select: EventsV9SelectSchema.optional(),
  data: z.union([ EventsV9CreateInputSchema,EventsV9UncheckedCreateInputSchema ]),
}).strict()

export const EventsV9UpsertArgsSchema: z.ZodType<Prisma.EventsV9UpsertArgs> = z.object({
  select: EventsV9SelectSchema.optional(),
  where: EventsV9WhereUniqueInputSchema,
  create: z.union([ EventsV9CreateInputSchema,EventsV9UncheckedCreateInputSchema ]),
  update: z.union([ EventsV9UpdateInputSchema,EventsV9UncheckedUpdateInputSchema ]),
}).strict()

export const EventsV9CreateManyArgsSchema: z.ZodType<Prisma.EventsV9CreateManyArgs> = z.object({
  data: EventsV9CreateManyInputSchema.array(),
  skipDuplicates: z.boolean().optional(),
}).strict()

export const EventsV9DeleteArgsSchema: z.ZodType<Prisma.EventsV9DeleteArgs> = z.object({
  select: EventsV9SelectSchema.optional(),
  where: EventsV9WhereUniqueInputSchema,
}).strict()

export const EventsV9UpdateArgsSchema: z.ZodType<Prisma.EventsV9UpdateArgs> = z.object({
  select: EventsV9SelectSchema.optional(),
  data: z.union([ EventsV9UpdateInputSchema,EventsV9UncheckedUpdateInputSchema ]),
  where: EventsV9WhereUniqueInputSchema,
}).strict()

export const EventsV9UpdateManyArgsSchema: z.ZodType<Prisma.EventsV9UpdateManyArgs> = z.object({
  data: z.union([ EventsV9UpdateManyMutationInputSchema,EventsV9UncheckedUpdateManyInputSchema ]),
  where: EventsV9WhereInputSchema.optional(),
}).strict()

export const EventsV9DeleteManyArgsSchema: z.ZodType<Prisma.EventsV9DeleteManyArgs> = z.object({
  where: EventsV9WhereInputSchema.optional(),
}).strict()

export const ItemsCreateArgsSchema: z.ZodType<Prisma.ItemsCreateArgs> = z.object({
  select: ItemsSelectSchema.optional(),
  data: z.union([ ItemsCreateInputSchema,ItemsUncheckedCreateInputSchema ]),
}).strict()

export const ItemsUpsertArgsSchema: z.ZodType<Prisma.ItemsUpsertArgs> = z.object({
  select: ItemsSelectSchema.optional(),
  where: ItemsWhereUniqueInputSchema,
  create: z.union([ ItemsCreateInputSchema,ItemsUncheckedCreateInputSchema ]),
  update: z.union([ ItemsUpdateInputSchema,ItemsUncheckedUpdateInputSchema ]),
}).strict()

export const ItemsCreateManyArgsSchema: z.ZodType<Prisma.ItemsCreateManyArgs> = z.object({
  data: ItemsCreateManyInputSchema.array(),
  skipDuplicates: z.boolean().optional(),
}).strict()

export const ItemsDeleteArgsSchema: z.ZodType<Prisma.ItemsDeleteArgs> = z.object({
  select: ItemsSelectSchema.optional(),
  where: ItemsWhereUniqueInputSchema,
}).strict()

export const ItemsUpdateArgsSchema: z.ZodType<Prisma.ItemsUpdateArgs> = z.object({
  select: ItemsSelectSchema.optional(),
  data: z.union([ ItemsUpdateInputSchema,ItemsUncheckedUpdateInputSchema ]),
  where: ItemsWhereUniqueInputSchema,
}).strict()

export const ItemsUpdateManyArgsSchema: z.ZodType<Prisma.ItemsUpdateManyArgs> = z.object({
  data: z.union([ ItemsUpdateManyMutationInputSchema,ItemsUncheckedUpdateManyInputSchema ]),
  where: ItemsWhereInputSchema.optional(),
}).strict()

export const ItemsDeleteManyArgsSchema: z.ZodType<Prisma.ItemsDeleteManyArgs> = z.object({
  where: ItemsWhereInputSchema.optional(),
}).strict()

interface EventsV9GetPayload extends HKT {
  readonly _A?: boolean | null | undefined | Prisma.EventsV9Args
  readonly type: Prisma.EventsV9GetPayload<this['_A']>
}

interface ItemsGetPayload extends HKT {
  readonly _A?: boolean | null | undefined | Prisma.ItemsArgs
  readonly type: Prisma.ItemsGetPayload<this['_A']>
}

export const tableSchemas = {
  eventsV9: {
    fields: ["cid","id","altm","nid","ttl","s1","estart","eend","istart","iend","loc","snz","ls","dsts","clr","rep","alld","ogn","fsn","attsts","oattsts","i1","i2","i3","i4","i5","i6","i7","i8","s2"],
    relations: [
    ],
    modelSchema: (EventsV9CreateInputSchema as any)
      .partial()
      .or((EventsV9UncheckedCreateInputSchema as any).partial()),
    createSchema: EventsV9CreateArgsSchema,
    createManySchema: EventsV9CreateManyArgsSchema,
    findUniqueSchema: EventsV9FindUniqueArgsSchema,
    findSchema: EventsV9FindFirstArgsSchema,
    updateSchema: EventsV9UpdateArgsSchema,
    updateManySchema: EventsV9UpdateManyArgsSchema,
    upsertSchema: EventsV9UpsertArgsSchema,
    deleteSchema: EventsV9DeleteArgsSchema,
    deleteManySchema: EventsV9DeleteManyArgsSchema
  } as TableSchema<
    z.infer<typeof EventsV9CreateInputSchema>,
    Prisma.EventsV9CreateArgs['data'],
    Prisma.EventsV9UpdateArgs['data'],
    Prisma.EventsV9FindFirstArgs['select'],
    Prisma.EventsV9FindFirstArgs['where'],
    Prisma.EventsV9FindUniqueArgs['where'],
    never,
    Prisma.EventsV9FindFirstArgs['orderBy'],
    Prisma.EventsV9ScalarFieldEnum,
    EventsV9GetPayload
  >,
  items: {
    fields: ["value"],
    relations: [
    ],
    modelSchema: (ItemsCreateInputSchema as any)
      .partial()
      .or((ItemsUncheckedCreateInputSchema as any).partial()),
    createSchema: ItemsCreateArgsSchema,
    createManySchema: ItemsCreateManyArgsSchema,
    findUniqueSchema: ItemsFindUniqueArgsSchema,
    findSchema: ItemsFindFirstArgsSchema,
    updateSchema: ItemsUpdateArgsSchema,
    updateManySchema: ItemsUpdateManyArgsSchema,
    upsertSchema: ItemsUpsertArgsSchema,
    deleteSchema: ItemsDeleteArgsSchema,
    deleteManySchema: ItemsDeleteManyArgsSchema
  } as TableSchema<
    z.infer<typeof ItemsCreateInputSchema>,
    Prisma.ItemsCreateArgs['data'],
    Prisma.ItemsUpdateArgs['data'],
    Prisma.ItemsFindFirstArgs['select'],
    Prisma.ItemsFindFirstArgs['where'],
    Prisma.ItemsFindUniqueArgs['where'],
    never,
    Prisma.ItemsFindFirstArgs['orderBy'],
    Prisma.ItemsScalarFieldEnum,
    ItemsGetPayload
  >,
}

export const schema = new DbSchema(tableSchemas, migrations)
export type Electric = ElectricClient<typeof schema>
