import { type DeviceAction } from '@midscene/core/device';
import type { NativeAction, NativeCapabilities } from './protocol';
export declare function extendedActions(execute: (action: NativeAction) => Promise<void>, capabilities?: NativeCapabilities): DeviceAction[];
