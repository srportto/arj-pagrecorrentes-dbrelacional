import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compara as copias manuais espelhadas do contrato de evento e falha (exit != 0) quando
 * divergem. Nao depende de build Maven de nenhum app - le os arquivos direto do disco, para
 * poder rodar como job de CI independente de modulo (ver design.md de
 * openspec/changes/rede-seguranca-contrato-evento, decisao D2).
 *
 * <p>Escopo da comparacao (.avsc): o CONJUNTO de campos e o tipo/nulabilidade de cada um -
 * reordenar a lista "fields" nao falha (o decoder Avro resolve por nome, nao por posicao), mas
 * inverter a ordem dentro de uma uniao de tipos (ex.: ["null","long"] vs ["long","null"]) falha,
 * porque isso muda qual branch e o default. "namespace"/"name" do schema (nivel raiz, fora de
 * "fields") nao entram na comparacao - uma divergencia ai geraria pacotes Java diferentes via
 * avro-maven-plugin e quebraria a compilacao de um dos lados por outro caminho.
 *
 * Uso: java VerificarContratoEventos.java [raiz-do-repo]
 */
public class VerificarContratoEventos {

    private static final Path AVSC_PRODUCER = Path.of(
            "apps/autorizacaostatus-producer/src/main/resources/avro/EventoAutorizacao.avsc");
    private static final Path AVSC_CONSUMER = Path.of(
            "apps/eventos-consumer/src/main/resources/avro/EventoAutorizacao.avsc");
    private static final Path PAYLOAD_COMMAND = Path.of(
            "apps/arj-contratocommand/src/main/java/br/com/srportto/contratocommand"
                    + "/application/eventos/AutorizacaoEventoPayload.java");
    private static final Path PAYLOAD_PRODUCER = Path.of(
            "apps/autorizacaostatus-producer/src/main/java/br/com/srportto"
                    + "/autorizacaostatusproducer/application/eventos/AutorizacaoEventoPayload.java");

    public static void main(String[] args) throws IOException {
        Path raiz = Path.of(args.length > 0 ? args[0] : ".").toAbsolutePath().normalize();

        List<String> divergencias = new ArrayList<>();
        divergencias.addAll(compararAvsc(raiz.resolve(AVSC_PRODUCER), raiz.resolve(AVSC_CONSUMER)));
        divergencias.addAll(compararPayload(raiz.resolve(PAYLOAD_COMMAND), raiz.resolve(PAYLOAD_PRODUCER)));

        if (!divergencias.isEmpty()) {
            System.err.println("FALHA: copias espelhadas do contrato de evento divergem:");
            for (String divergencia : divergencias) {
                System.err.println("  - " + divergencia);
            }
            System.exit(1);
        }

        System.out.println("OK: as copias espelhadas do contrato de evento estao sincronizadas.");
        System.out.println("  - " + AVSC_PRODUCER + " == " + AVSC_CONSUMER);
        System.out.println("  - " + PAYLOAD_COMMAND + " == " + PAYLOAD_PRODUCER);
    }

    // --- Comparacao dos dois .avsc (Avro) ---

    private static List<String> compararAvsc(Path arquivo1, Path arquivo2) throws IOException {
        Map<String, Object> schema1 = (Map<String, Object>) new JsonParser(Files.readString(arquivo1)).parse();
        Map<String, Object> schema2 = (Map<String, Object>) new JsonParser(Files.readString(arquivo2)).parse();

        Map<String, Map<String, Object>> campos1 = camposPorNome(schema1);
        Map<String, Map<String, Object>> campos2 = camposPorNome(schema2);

        return compararConjuntosDeCampos(campos1, campos2, arquivo1, arquivo2,
                campo -> canonico(semNome(campo)));
    }

    private static Map<String, Map<String, Object>> camposPorNome(Map<String, Object> schema) {
        List<Object> fields = (List<Object>) schema.get("fields");
        Map<String, Map<String, Object>> porNome = new LinkedHashMap<>();
        for (Object field : fields) {
            Map<String, Object> campo = (Map<String, Object>) field;
            porNome.put((String) campo.get("name"), campo);
        }
        return porNome;
    }

    private static Map<String, Object> semNome(Map<String, Object> campo) {
        Map<String, Object> copia = new TreeMap<>(campo);
        copia.remove("name");
        copia.remove("doc");
        return copia;
    }

    // --- Comparacao dos dois AutorizacaoEventoPayload.java (record) ---

    private static final Pattern COMPONENTE_RECORD = Pattern.compile(
            "@JsonProperty\\(\\s*\"([^\"]+)\"\\s*\\)\\s+([A-Za-z0-9_.]+(?:<[^>]*>)?)\\s+([A-Za-z0-9_]+)\\s*[,)]");

    private static List<String> compararPayload(Path arquivo1, Path arquivo2) throws IOException {
        Map<String, Map<String, Object>> campos1 = componentesDoRecord(Files.readString(arquivo1));
        Map<String, Map<String, Object>> campos2 = componentesDoRecord(Files.readString(arquivo2));

        return compararConjuntosDeCampos(campos1, campos2, arquivo1, arquivo2,
                campo -> (String) campo.get("tipo"));
    }

    private static Map<String, Map<String, Object>> componentesDoRecord(String conteudo) {
        Map<String, Map<String, Object>> porJsonProperty = new LinkedHashMap<>();
        Matcher matcher = COMPONENTE_RECORD.matcher(conteudo);
        while (matcher.find()) {
            Map<String, Object> componente = new TreeMap<>();
            componente.put("tipo", matcher.group(2));
            componente.put("campoJava", matcher.group(3));
            porJsonProperty.put(matcher.group(1), componente);
        }
        return porJsonProperty;
    }

    // --- Motor de comparacao comum (nomes de campo + valor semantico) ---

    private interface RepresentacaoSemantica {
        String de(Map<String, Object> campo);
    }

    private static List<String> compararConjuntosDeCampos(
            Map<String, Map<String, Object>> campos1, Map<String, Map<String, Object>> campos2,
            Path arquivo1, Path arquivo2, RepresentacaoSemantica representacao) {

        List<String> divergencias = new ArrayList<>();

        for (String nomeCampo : campos1.keySet()) {
            if (!campos2.containsKey(nomeCampo)) {
                divergencias.add("campo '%s' existe em %s mas nao em %s".formatted(
                        nomeCampo, arquivo1, arquivo2));
            }
        }
        for (String nomeCampo : campos2.keySet()) {
            if (!campos1.containsKey(nomeCampo)) {
                divergencias.add("campo '%s' existe em %s mas nao em %s".formatted(
                        nomeCampo, arquivo2, arquivo1));
            }
        }
        for (String nomeCampo : campos1.keySet()) {
            if (!campos2.containsKey(nomeCampo)) {
                continue;
            }
            String repr1 = representacao.de(campos1.get(nomeCampo));
            String repr2 = representacao.de(campos2.get(nomeCampo));
            if (!repr1.equals(repr2)) {
                divergencias.add("campo '%s' diverge entre %s (%s) e %s (%s)".formatted(
                        nomeCampo, arquivo1, repr1, arquivo2, repr2));
            }
        }
        return divergencias;
    }

    // --- Serializacao canonica (para comparacao estrutural, tolerante a formatacao) ---

    private static String canonico(Object valor) {
        StringBuilder sb = new StringBuilder();
        canonico(valor, sb);
        return sb.toString();
    }

    private static void canonico(Object valor, StringBuilder sb) {
        switch (valor) {
            case null -> sb.append("null");
            case Map<?, ?> mapa -> {
                sb.append('{');
                boolean primeiro = true;
                for (var entrada : new TreeMap<>((Map<String, Object>) mapa).entrySet()) {
                    if (!primeiro) {
                        sb.append(',');
                    }
                    primeiro = false;
                    canonico(entrada.getKey(), sb);
                    sb.append(':');
                    canonico(entrada.getValue(), sb);
                }
                sb.append('}');
            }
            case List<?> lista -> {
                sb.append('[');
                for (int i = 0; i < lista.size(); i++) {
                    if (i > 0) {
                        sb.append(',');
                    }
                    canonico(lista.get(i), sb);
                }
                sb.append(']');
            }
            case String texto -> sb.append('"').append(texto.replace("\"", "\\\"")).append('"');
            default -> sb.append(valor);
        }
    }

    /** Parser JSON minimo (object, array, string, number, boolean, null) - sem dependencias externas. */
    private static final class JsonParser {
        private final String texto;
        private int pos;

        JsonParser(String texto) {
            this.texto = texto;
        }

        Object parse() {
            pularEspacos();
            Object valor = parseValor();
            pularEspacos();
            return valor;
        }

        private Object parseValor() {
            pularEspacos();
            char c = texto.charAt(pos);
            return switch (c) {
                case '{' -> parseObjeto();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't', 'f' -> parseBoolean();
                case 'n' -> parseNull();
                default -> parseNumero();
            };
        }

        private Map<String, Object> parseObjeto() {
            Map<String, Object> objeto = new LinkedHashMap<>();
            esperar('{');
            pularEspacos();
            if (texto.charAt(pos) == '}') {
                pos++;
                return objeto;
            }
            while (true) {
                pularEspacos();
                String chave = parseString();
                pularEspacos();
                esperar(':');
                Object valor = parseValor();
                objeto.put(chave, valor);
                pularEspacos();
                char c = texto.charAt(pos);
                if (c == ',') {
                    pos++;
                } else if (c == '}') {
                    pos++;
                    break;
                } else {
                    throw new IllegalStateException("JSON invalido na posicao " + pos);
                }
            }
            return objeto;
        }

        private List<Object> parseArray() {
            List<Object> lista = new ArrayList<>();
            esperar('[');
            pularEspacos();
            if (texto.charAt(pos) == ']') {
                pos++;
                return lista;
            }
            while (true) {
                lista.add(parseValor());
                pularEspacos();
                char c = texto.charAt(pos);
                if (c == ',') {
                    pos++;
                } else if (c == ']') {
                    pos++;
                    break;
                } else {
                    throw new IllegalStateException("JSON invalido na posicao " + pos);
                }
            }
            return lista;
        }

        private String parseString() {
            esperar('"');
            StringBuilder sb = new StringBuilder();
            while (texto.charAt(pos) != '"') {
                char c = texto.charAt(pos);
                if (c == '\\') {
                    pos++;
                    char escapado = texto.charAt(pos);
                    sb.append(switch (escapado) {
                        case 'n' -> '\n';
                        case 't' -> '\t';
                        case 'r' -> '\r';
                        default -> escapado;
                    });
                } else {
                    sb.append(c);
                }
                pos++;
            }
            pos++;
            return sb.toString();
        }

        private Object parseBoolean() {
            if (texto.startsWith("true", pos)) {
                pos += 4;
                return Boolean.TRUE;
            }
            if (texto.startsWith("false", pos)) {
                pos += 5;
                return Boolean.FALSE;
            }
            throw new IllegalStateException("JSON invalido na posicao " + pos);
        }

        private Object parseNull() {
            if (texto.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            throw new IllegalStateException("JSON invalido na posicao " + pos);
        }

        private Object parseNumero() {
            int inicio = pos;
            while (pos < texto.length() && "-+.eE0123456789".indexOf(texto.charAt(pos)) >= 0) {
                pos++;
            }
            String numero = texto.substring(inicio, pos);
            return numero.contains(".") || numero.contains("e") || numero.contains("E")
                    ? (Object) Double.parseDouble(numero)
                    : (Object) Long.parseLong(numero);
        }

        private void pularEspacos() {
            while (pos < texto.length() && Character.isWhitespace(texto.charAt(pos))) {
                pos++;
            }
        }

        private void esperar(char esperado) {
            if (texto.charAt(pos) != esperado) {
                throw new IllegalStateException("Esperado '" + esperado + "' na posicao " + pos);
            }
            pos++;
        }
    }
}
