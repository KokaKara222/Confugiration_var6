import java.io.File
import java.net.URI
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * Пользовательские настройки CLI-приложения.
 *
 * @property packageName имя анализируемого пакета
 * @property repository URL репозитория или путь к тестовому репозиторию
 * @property testMode режим тестирования
 */
data class AppConfig(
    val packageName: String,
    val repository: String,
    val testMode: Boolean
)

/**
 * Читает единственное значение указанного XML-тега.
 *
 * @param root корневой XML-элемент
 * @param tagName имя требуемого тега
 * @return непустое значение тега
 * @throws IllegalArgumentException если тег отсутствует, повторяется или пуст
 */
fun readRequiredTag(root: Element, tagName: String): String {
    val nodes = root.getElementsByTagName(tagName)
    require(nodes.length == 1) {
        "Параметр '$tagName' должен быть указан ровно один раз."
    }
    val value = nodes.item(0).textContent.trim()
    require(value.isNotEmpty()) { "Параметр '$tagName' не может быть пустым." }
    return value
}

/**
 * Проверяет имя пакета и значение режима тестирования.
 *
 * @param packageName имя пакета из конфигурации
 * @param testModeText текстовое значение режима
 * @return пара проверенных значений
 */
fun validateBasicSettings(
    packageName: String,
    testModeText: String
): Pair<String, Boolean> {
    require(packageName.matches(Regex("[A-Za-z0-9_-]+"))) {
        "Некорректное имя пакета: используйте латинские буквы, цифры, '_' или '-'."
    }
    val testMode = when (testModeText.lowercase()) {
        "true" -> true
        "false" -> false
        else -> throw IllegalArgumentException(
            "Параметр 'testMode' должен иметь значение true или false."
        )
    }
    return Pair(packageName, testMode)
}

/**
 * Проверяет адрес репозитория с учётом выбранного режима.
 *
 * @param repository URL или путь из XML
 * @param testMode режим тестирования
 * @return нормализованное значение репозитория
 */
fun validateRepository(repository: String, testMode: Boolean): String {
    if (testMode) {
        val file = File(repository)
        require(file.exists() && file.isFile && file.canRead()) {
            "В тестовом режиме путь '$repository' должен указывать на доступный файл."
        }
        return file.canonicalPath
    }

    val uri = try {
        URI(repository)
    } catch (exception: Exception) {
        throw IllegalArgumentException("Некорректный URL репозитория.")
    }
    require(uri.scheme == "http" || uri.scheme == "https") {
        "URL репозитория должен начинаться с http:// или https://."
    }
    require(!uri.host.isNullOrBlank()) { "В URL репозитория не указано имя хоста." }
    return repository.trimEnd('/')
}

/**
 * Загружает и проверяет настройки из XML-файла.
 *
 * @param configPath путь к XML-файлу конфигурации
 * @return проверенная конфигурация приложения
 */
fun loadConfig(configPath: String): AppConfig {
    val configFile = File(configPath)
    require(configFile.exists() && configFile.isFile && configFile.canRead()) {
        "Файл конфигурации '$configPath' не найден или недоступен для чтения."
    }

    val factory = DocumentBuilderFactory.newInstance()
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
    factory.setXIncludeAware(false)
    factory.isExpandEntityReferences = false

    val document = factory.newDocumentBuilder().parse(configFile)
    val root = document.documentElement
    require(root.tagName == "config") { "Корневой XML-элемент должен называться config." }

    val packageName = readRequiredTag(root, "package")
    val repository = readRequiredTag(root, "repository")
    val testModeText = readRequiredTag(root, "testMode")
    val (checkedName, testMode) = validateBasicSettings(packageName, testModeText)
    val checkedRepository = validateRepository(repository, testMode)

    return AppConfig(checkedName, checkedRepository, testMode)
}

/**
 * Выводит настройки в формате ключ-значение.
 *
 * @param config проверенная конфигурация
 */
fun printConfig(config: AppConfig) {
    println("Параметры конфигурации:")
    println("package = ${config.packageName}")
    println("repository = ${config.repository}")
    println("testMode = ${config.testMode}")
}

/**
 * Запускает CLI-приложение и сообщает об ошибках конфигурации.
 *
 * @param args аргументы командной строки; первый аргумент — путь к XML
 */
fun main(args: Array<String>) {
    val configPath = args.firstOrNull() ?: "config.xml"
    try {
        val config = loadConfig(configPath)
        printConfig(config)
        println("Этап 1 завершён: настройки успешно загружены.")
    } catch (exception: Exception) {
        System.err.println("Ошибка: ${exception.message ?: "не удалось прочитать конфигурацию."}")
        System.err.println("Использование: ./run.sh [путь-к-config.xml]")
        kotlin.system.exitProcess(1)
    }
}
