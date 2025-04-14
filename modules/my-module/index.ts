import { NativeModulesProxy, EventEmitter, Subscription } from 'expo-modules-core';
// import * as PSYNC from '../../lib/powersync'; 

// Import the native module. On web, it will be resolved to MyModule.web.ts
// and on native platforms to MyModule.ts
import MyModule from './src/MyModule';
import MyModuleView from './src/MyModuleView';
import { ChangeEventPayload, MyModuleViewProps } from './src/MyModule.types';

// Get the native constant value.
export const PI = MyModule.PI;

export function hello(): string {
  return MyModule.hello();
}

export interface RescheduleConfirmation {
  event_id: number;
  calendar_id: number;
  original_instance_start_time: number;
  title: string;
  new_instance_start_time: number;
  is_in_future: boolean;
  meta?: string;
  created_at: string;
  updated_at: string;
}

export interface RawRescheduleConfirmation {
  event_id: number;
  calendar_id: number;
  original_instance_start_time: number;
  title: string;
  new_instance_start_time: number;
  is_in_future: number;
  meta?: string;
  created_at: string;
  updated_at: string;
}

function convertToRescheduleConfirmation(raw: RawRescheduleConfirmation): RescheduleConfirmation {
  return {
    ...raw,
    is_in_future: raw.is_in_future === 1
  };
}

export async function setValueAsync(value: RawRescheduleConfirmation[]) {
  try {
    const converted = value.map(convertToRescheduleConfirmation);
    return await MyModule.setValueAsync(JSON.stringify(converted));
  } catch (error) {
    console.error('Error in setValueAsync:', error);
    throw error;
  }
}

const emitter = new EventEmitter(MyModule ?? NativeModulesProxy.MyModule);

export function addChangeListener(listener: (event: ChangeEventPayload) => void): Subscription {
  return emitter.addListener<ChangeEventPayload>('onChange', listener);
}

export { MyModuleView, type MyModuleViewProps, type ChangeEventPayload };

export default MyModule;
