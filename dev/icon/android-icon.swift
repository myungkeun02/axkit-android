// Android adaptive icon layers for the app icon C ("bot team"), drawn the same way as ax-kit-ios dev/icon/icons.swift.
// Run: swift dev/icon/android-icon.swift <folder>
//  → foreground.png (the three bots on transparent), monochrome.png (bot silhouettes, eyes cut out), 1024×1024 each.
// The iOS icon's whole square maps to the 72dp visible part of the 108dp layer, so the bots sit inside the safe zone.

import AppKit

let S: CGFloat = 1024
let out = CommandLine.arguments[1]
let scale: CGFloat = 72.0 / 108.0
func rgb(_ r: Int, _ g: Int, _ b: Int, _ a: CGFloat = 1) -> CGColor {
    CGColor(srgbRed: CGFloat(r) / 255, green: CGFloat(g) / 255, blue: CGFloat(b) / 255, alpha: a)
}

func render(_ name: String, _ draw: (CGContext) -> Void) {
    let cs = CGColorSpace(name: CGColorSpace.sRGB)!
    let ctx = CGContext(data: nil, width: Int(S), height: Int(S), bitsPerComponent: 8, bytesPerRow: 0, space: cs,
                        bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
    ctx.clear(CGRect(x: 0, y: 0, width: S, height: S))
    ctx.translateBy(x: 0, y: S); ctx.scaleBy(x: 1, y: -1) // top-left origin
    // Same coordinates as the iOS 1024 canvas, shrunk around the centre.
    ctx.translateBy(x: S / 2, y: S / 2); ctx.scaleBy(x: scale, y: scale); ctx.translateBy(x: -S / 2, y: -S / 2)
    draw(ctx)
    let rep = NSBitmapImageRep(cgImage: ctx.makeImage()!)
    try! rep.representation(using: .png, properties: [:])!.write(to: URL(fileURLWithPath: "\(out)/\(name).png"))
}

func squircle(_ rect: CGRect, n: CGFloat = 2.25) -> CGPath {
    let path = CGMutablePath()
    let steps = 240
    for i in 0...steps {
        let t = CGFloat(i) / CGFloat(steps) * 2 * .pi
        let ct = cos(t), st = sin(t)
        let x = rect.midX + rect.width / 2 * (ct >= 0 ? 1 : -1) * pow(abs(ct), 2 / n)
        let y = rect.midY + rect.height / 2 * (st >= 0 ? 1 : -1) * pow(abs(st), 2 / n)
        i == 0 ? path.move(to: CGPoint(x: x, y: y)) : path.addLine(to: CGPoint(x: x, y: y))
    }
    path.closeSubpath()
    return path
}

func blob(_ c: CGContext, rect: CGRect, fill: CGColor, shade: CGColor) {
    let path = squircle(rect)
    c.saveGState()
    c.setFillColor(rgb(0, 20, 80, 0.22))
    c.fillEllipse(in: CGRect(x: rect.midX - rect.width * 0.36, y: rect.maxY - rect.height * 0.02, width: rect.width * 0.72, height: rect.height * 0.12))
    c.restoreGState()
    c.saveGState()
    c.addPath(path); c.clip()
    let g = CGGradient(colorsSpace: nil, colors: [fill, shade] as CFArray, locations: [0.35, 1])!
    c.drawLinearGradient(g, start: CGPoint(x: 0, y: rect.minY), end: CGPoint(x: 0, y: rect.maxY), options: [])
    c.setFillColor(rgb(255, 255, 255, 0.55))
    c.fillEllipse(in: CGRect(x: rect.minX + rect.width * 0.2, y: rect.minY + rect.height * 0.1, width: rect.width * 0.26, height: rect.height * 0.12))
    c.restoreGState()
}

func eyes(_ c: CGContext, center: CGPoint, gap: CGFloat, w: CGFloat, h: CGFloat, color: CGColor) {
    c.setFillColor(color)
    for dx in [-gap / 2, gap / 2] {
        let r = CGRect(x: center.x + dx - w / 2, y: center.y - h / 2, width: w, height: h)
        c.addPath(CGPath(roundedRect: r, cornerWidth: w / 2, cornerHeight: w / 2, transform: nil)); c.fillPath()
    }
}

// The team from icons.swift "C-team", without the blue background.
let left = CGRect(x: 150, y: 330, width: 330, height: 300), right = CGRect(x: 544, y: 330, width: 330, height: 300)
let lead = CGRect(x: 292, y: 420, width: 440, height: 400)
render("foreground") { c in
    blob(c, rect: left, fill: rgb(0xBF, 0xF0, 0xD6), shade: rgb(0x8F, 0xD9, 0xB4))
    eyes(c, center: CGPoint(x: 315, y: 480), gap: 96, w: 38, h: 66, color: rgb(0x1B, 0x2A, 0x4A, 0.85))
    blob(c, rect: right, fill: rgb(0xFF, 0xD9, 0xB0), shade: rgb(0xF5, 0xB8, 0x80))
    eyes(c, center: CGPoint(x: 709, y: 480), gap: 96, w: 38, h: 66, color: rgb(0x1B, 0x2A, 0x4A, 0.85))
    blob(c, rect: lead, fill: rgb(255, 255, 255), shade: rgb(214, 226, 255))
    eyes(c, center: CGPoint(x: S / 2, y: 620), gap: 130, w: 50, h: 88, color: rgb(0x1B, 0x2A, 0x4A))
}

// Themed icon: the system tints the alpha. Three silhouettes; the lead is ringed so it reads in front; eyes cut out.
render("monochrome") { c in
    c.setFillColor(rgb(255, 255, 255))
    c.addPath(squircle(left)); c.fillPath()
    c.addPath(squircle(right)); c.fillPath()
    c.setBlendMode(.clear)
    c.addPath(squircle(lead.insetBy(dx: -22, dy: -22))); c.fillPath()
    c.setBlendMode(.normal)
    c.addPath(squircle(lead)); c.fillPath()
    c.setBlendMode(.clear)
    eyes(c, center: CGPoint(x: 315, y: 480), gap: 96, w: 38, h: 66, color: rgb(0, 0, 0))
    eyes(c, center: CGPoint(x: 709, y: 480), gap: 96, w: 38, h: 66, color: rgb(0, 0, 0))
    eyes(c, center: CGPoint(x: S / 2, y: 620), gap: 130, w: 50, h: 88, color: rgb(0, 0, 0))
}
print("ok")
