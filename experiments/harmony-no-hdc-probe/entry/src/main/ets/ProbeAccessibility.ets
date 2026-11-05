import { AccessibilityExtensionAbility } from '@kit.AccessibilityKit';
import { hilog } from '@kit.PerformanceAnalysisKit';
export default class ProbeAccessibility extends AccessibilityExtensionAbility { onConnect(): void { hilog.info(0, 'TapilotProbe', 'ACCESSIBILITY_CONNECTED'); } }
