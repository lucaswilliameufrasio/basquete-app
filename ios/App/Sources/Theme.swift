import SwiftUI

public enum JirauTheme {
    public static let primaryColor = Color(red: 0.12, green: 0.34, blue: 0.19)
    public static let accentColor = Color(red: 0.16, green: 0.32, blue: 0.75)
    public static let earthColor = Color(red: 0.82, green: 0.49, blue: 0.18)
    public static let cornerRadius: CGFloat = 14.0
    static let background = adaptive(light: 0xF4F4F4, dark: 0x101010)
    static let surface = adaptive(light: 0xFFFFFF, dark: 0x1C1C1C)
    static let textPrimary = adaptive(light: 0x171717, dark: 0xF5F5F5)
    static let textSecondary = adaptive(light: 0x595959, dark: 0xBCBCBC)
    static let accent = adaptive(light: 0xA93F00, dark: 0xFF7A1A)

    private static func adaptive(light: UInt32, dark: UInt32) -> Color {
        Color(uiColor: UIColor { traits in
            let value = traits.userInterfaceStyle == .dark ? dark : light
            return UIColor(
                red: CGFloat((value >> 16) & 0xFF) / 255,
                green: CGFloat((value >> 8) & 0xFF) / 255,
                blue: CGFloat(value & 0xFF) / 255,
                alpha: 1
            )
        })
    }
}

public struct JirauCardModifier: ViewModifier {
    public func body(content: Content) -> some View {
        content
            .padding()
            .background(Color(.secondarySystemBackground))
            .clipShape(RoundedRectangle(cornerRadius: JirauTheme.cornerRadius, style: .continuous))
            .shadow(color: Color.black.opacity(0.04), radius: 6, x: 0, y: 3)
    }
}

public extension View {
    func jirauCardStyle() -> some View {
        modifier(JirauCardModifier())
    }
}
