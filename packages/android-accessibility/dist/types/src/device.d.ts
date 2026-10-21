import type { AbstractInterface, DeviceAction, InputPrimitives } from '@midscene/core/device';
import { type Size } from '@midscene/core';
import type { NativeBridge, NativeCapabilities } from './protocol';
export declare class AndroidAccessibilityDevice implements AbstractInterface {
    private readonly bridge;
    private readonly capabilities?;
    interfaceType: string;
    readonly inputPrimitives: InputPrimitives;
    private latestFrame?;
    private repeatedGesture?;
    private repeatedGestureCount;
    private latestPixels?;
    private gesturePixels?;
    private recentSwipes;
    constructor(bridge: NativeBridge, capabilities?: NativeCapabilities | undefined);
    screenshotBase64(): Promise<string>;
    size(): Promise<Size>;
    actionSpace(): DeviceAction[];
    describe(): string;
    destroy(): Promise<void>;
    private execute;
    private guardRepeatedGesture;
    private scroll;
}
