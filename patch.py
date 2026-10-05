import re, sys

path = "app/src/main/java/com/nikeboss/keyboard/KeyboardView.kt"
src = open(path, encoding="utf-8").read()

# 1. Отступы между клавишами: 3dp -> 5dp
src, n1 = re.subn(
    r"private val pad = 3f \* dp",
    "private val pad = 5f * dp",
    src)

# 2. Поиск клавиши: берём ближайшую по X (промахи в щель и у краёв не теряются)
new_key_at = '''    private fun keyAt(x: Float, y: Float): Key? {
        if (rows.isEmpty() || height == 0) return null
        val rowHeight = height.toFloat() / rows.size
        val r = (y / rowHeight).toInt().coerceIn(0, rows.size - 1)
        var best: Key? = null
        var bestDist = Float.MAX_VALUE
        for (k in rows[r]) {
            if (k.kind == Kind.SPACER) continue
            val d = when {
                x < k.rect.left -> k.rect.left - x
                x > k.rect.right -> x - k.rect.right
                else -> 0f
            }
            if (d < bestDist) {
                bestDist = d
                best = k
            }
        }
        return best
    }

    @SuppressLint'''
src, n2 = re.subn(
    r"    private fun keyAt\(.*?\n    @SuppressLint",
    lambda m: new_key_at,
    src, flags=re.DOTALL)

# 3. Запятая с пробелом и Shift после . ! ?
new_press = '''service?.onKeyPress(text.codePointAt(0), commaSpace = !symbols && text == ",")
                if (shift == ShiftState.ONCE) shift = ShiftState.OFF
                // после конца предложения следующая буква заглавная (Caps Lock не трогаем)
                if (!symbols && shift == ShiftState.OFF && (text == "." || text == "!" || text == "?")) {
                    shift = ShiftState.ONCE
                }'''
src, n3 = re.subn(
    r"service\?\.onKeyPress\(text\.codePointAt\(0\)\)\s*\n\s*if \(shift == ShiftState\.ONCE\) shift = ShiftState\.OFF",
    lambda m: new_press,
    src)

print("замен:", n1, n2, n3)
if (n1, n2, n3) != (1, 1, 1):
    print("Что-то не найдено, файл НЕ изменён. Пришли мне вывод.")
    sys.exit(1)

open(path, "w", encoding="utf-8").write(src)
print("Готово")
