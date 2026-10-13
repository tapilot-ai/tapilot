import AppKit
import ScreenCaptureKit
import ApplicationServices

@main struct MirrorBridge {
  @MainActor static func main() async {
    _ = NSApplication.shared
    _ = CGMainDisplayID()
    do {
      let args = Array(CommandLine.arguments.dropFirst())
      guard let command = args.first else { throw error("Expected capture or tap") }
      guard CGPreflightScreenCaptureAccess() else { throw error("Screen Recording permission is required for this helper's host process") }
      let content = try await SCShareableContent.excludingDesktopWindows(true, onScreenWindowsOnly: false)
      let candidates = content.windows.filter { $0.owningApplication?.bundleIdentifier == "com.apple.ScreenContinuity" && $0.frame.width > 100 && $0.frame.height > 200 && ($0.title == "iPhone镜像" || $0.title == "iPhone Mirroring") }
      if command == "windows" { printJSON(["windows": candidates.map { ["id": $0.windowID, "title": $0.title ?? "", "width": $0.frame.width, "height": $0.frame.height, "onScreen": $0.isOnScreen] as [String: Any] }]); return }
      guard candidates.count == 1, let window = candidates.first else { throw error("Expected one iPhone Mirroring window; found \(candidates.count). Open and connect iPhone Mirroring first.") }
      let bounds = window.frame
      let geometry: [String: Any] = ["windowId": window.windowID, "x": bounds.minX, "y": bounds.minY, "width": bounds.width, "height": bounds.height, "pid": window.owningApplication!.processID, "frontmostApp": NSWorkspace.shared.frontmostApplication?.bundleIdentifier ?? "unknown"]
      if command == "capture" {
        let config = SCStreamConfiguration()
        config.width = Int(bounds.width * 2); config.height = Int(bounds.height * 2)
        config.showsCursor = false
        let filter = SCContentFilter(desktopIndependentWindow: window)
        let image = try await SCScreenshotManager.captureImage(contentFilter: filter, configuration: config)
        let rep = NSBitmapImageRep(cgImage: image)
        guard let png = rep.representation(using: .png, properties: [:]) else { throw error("PNG encoding failed") }
        printJSON(["geometry": geometry, "dataUrl": "data:image/png;base64," + png.base64EncodedString(), "width": image.width, "height": image.height])
      } else if command == "tap" {
        guard AXIsProcessTrusted() else { throw error("Accessibility permission is required for input") }
        guard args.count == 3, let x = Double(args[1]), let y = Double(args[2]), x >= 0, y >= 0, x < bounds.width, y < bounds.height else { throw error("Tap outside window") }
        let point = CGPoint(x: bounds.minX + x, y: bounds.minY + y)
        for kind in [CGEventType.leftMouseDown, .leftMouseUp] {
          guard let nativeEvent = NSEvent.mouseEvent(with: kind == .leftMouseDown ? .leftMouseDown : .leftMouseUp, location: NSPoint(x: x, y: bounds.height - y), modifierFlags: [], timestamp: ProcessInfo.processInfo.systemUptime, windowNumber: Int(window.windowID), context: nil, eventNumber: 0, clickCount: 1, pressure: kind == .leftMouseDown ? 1 : 0), let event = nativeEvent.cgEvent else { throw error("Cannot create input event") }
          event.location = point
          event.setIntegerValueField(.mouseEventClickState, value: 1)
          event.setIntegerValueField(.mouseEventWindowUnderMousePointer, value: Int64(window.windowID))
          event.setIntegerValueField(.mouseEventWindowUnderMousePointerThatCanHandleThisEvent, value: Int64(window.windowID))
          event.postToPid(window.owningApplication!.processID)
          if kind == .leftMouseDown { try await Task.sleep(for: .milliseconds(80)) }
        }
        printJSON(["sent": true, "verified": false, "geometry": geometry])
      } else { throw error("Unsupported command") }
    } catch { printJSON(["error": error.localizedDescription]); exit(1) }
  }
  static func error(_ text: String) -> NSError { NSError(domain: "MirrorBridge", code: 1, userInfo: [NSLocalizedDescriptionKey: text]) }
  static func printJSON(_ value: [String: Any]) { print(String(data: try! JSONSerialization.data(withJSONObject: value, options: [.sortedKeys]), encoding: .utf8)!) }
}
