import probe from 'libprobe.so';
import { accessibility } from '@kit.AccessibilityKit';
import { BusinessError } from '@kit.BasicServicesKit';
@Entry
@Component
struct Index {
 @State extensions: string = 'Checking accessibility extensions...';
 checkAccessibility(): void {
   try {
     const installed = accessibility.getAccessibilityExtensionListSync('all', 'install');
     const enabled = accessibility.getAccessibilityExtensionListSync('all', 'enable');
     this.extensions = 'Installed total: ' + installed.length + '\nOur installed extension: ' + JSON.stringify(installed.filter(item => item.bundleName === 'md.lifeos.tapilot.probe')) + '\nEnabled total: ' + enabled.length + '\nOur enabled extension: ' + JSON.stringify(enabled.filter(item => item.bundleName === 'md.lifeos.tapilot.probe'));
   } catch (err) { this.extensions = 'Accessibility query error: ' + (err as BusinessError).code + ' ' + (err as BusinessError).message; }
 }
 @State result: string = 'Ready. Ordinary app, no special permissions.';
 aboutToAppear(): void { this.checkAccessibility(); setTimeout(() => { this.result = probe.requestInjection(); }, 15000); }
 build() { Column({space: 20}) { Text('Tapilot no-HDC probe').fontSize(24); Text(this.result).fontSize(16); Text(this.extensions).fontSize(12); Button('Request input injection').onClick(() => { this.result = probe.requestInjection(); }); }.width('100%').height('100%').padding(24) }
}
