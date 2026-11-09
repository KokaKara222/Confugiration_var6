#!/usr/bin/env bash
set -euo pipefail

if [[ ! -f src/main/kotlin/Main.kt ]]; then
    echo "Ошибка: запусти скрипт из папки проекта с run.sh."
    exit 1
fi

for tool in java kotlinc; do
    if ! command -v "$tool" >/dev/null 2>&1; then
        echo "Не установлен $tool."
        exit 1
    fi
done

temp_dir=$(mktemp -d)
trap 'rm -rf "$temp_dir"' EXIT

echo "Собираю приложение..."
kotlinc src/main/kotlin/Main.kt \
    -include-runtime -d "$temp_dir/app.jar"

printf '[dependencies]\nserde = "1.0"\n' > "$temp_dir/Cargo.toml"

total=0
passed=0

# Создаёт временную конфигурацию.
make_config() {
    printf '<config>\n<package>%s</package>\n' "$1"
    printf '<repository>%s</repository>\n' "$2"
    printf '<testMode>%s</testMode>\n</config>\n' "$3"
}

# Проверяет сообщение об ошибке и код завершения.
check_error() {
    local title="$1"
    local expected="$2"
    local status=0

    total=$((total + 1))
    echo
    echo "===== $total. $title ====="

    java -jar "$temp_dir/app.jar" "$temp_dir/config.xml" \
        > "$temp_dir/result.txt" 2>&1 || status=$?

    cat "$temp_dir/result.txt"

    if [[ "$status" -eq 1 ]] &&
        grep -Fq "$expected" "$temp_dir/result.txt"; then
        echo "Результат: OK"
        passed=$((passed + 1))
    else
        echo "Результат: НЕ ПРОЙДЕНА, код завершения $status"
    fi
}

# Отсутствующие, повторяющиеся и пустые теги.
for tag in package repository testMode; do
    make_config demo "$temp_dir/Cargo.toml" true \
        > "$temp_dir/base.xml"

    sed "/<$tag>/d" "$temp_dir/base.xml" \
        > "$temp_dir/config.xml"
    check_error "Отсутствует тег $tag" "$tag"

    sed "s|</config>|<$tag>demo</$tag></config>|" \
        "$temp_dir/base.xml" > "$temp_dir/config.xml"
    check_error "Повторяется тег $tag" "$tag"

    sed "s|<$tag>.*</$tag>|<$tag> </$tag>|" \
        "$temp_dir/base.xml" > "$temp_dir/config.xml"
    check_error "Пустой тег $tag" "$tag"
done

make_config "bad name!" "$temp_dir/Cargo.toml" true \
    > "$temp_dir/config.xml"
check_error "Неправильное имя пакета" "имя пакета"

make_config demo "$temp_dir/Cargo.toml" yes \
    > "$temp_dir/config.xml"
check_error "Неправильный testMode" "testMode"

make_config demo "$temp_dir/missing.toml" true \
    > "$temp_dir/config.xml"
check_error "Файл репозитория отсутствует" "доступный файл"

make_config demo "$temp_dir" true \
    > "$temp_dir/config.xml"
check_error "Каталог вместо файла" "доступный файл"

make_config demo "ftp://crates.io" false \
    > "$temp_dir/config.xml"
check_error "Неправильная схема URL" "http://"

make_config demo "https:///missing-host" false \
    > "$temp_dir/config.xml"
check_error "URL без имени хоста" "хоста"

make_config demo "https://bad host" false \
    > "$temp_dir/config.xml"
check_error "Некорректный URL" "Некорректный URL"

make_config demo "$temp_dir/Cargo.toml" true |
    sed 's/config/settings/g' > "$temp_dir/config.xml"
check_error "Неправильный корневой тег" "Корневой"

printf '<config><package>' > "$temp_dir/config.xml"
check_error "Повреждённый XML" "Ошибка:"

rm "$temp_dir/config.xml"
check_error "Файл конфигурации отсутствует" "Файл конфигурации"

echo
echo "Итого: $passed/$total проверок пройдено."
echo "Проверены ошибки конфигурации, без сетевых запросов."

if [[ "$passed" -ne "$total" ]]; then
    exit 1
fi
