package io.multiagent.core.weaviate;

import io.multiagent.core.model.ExpenseItem;
import io.weaviate.client.v1.graphql.model.GraphQLResponse;
import lombok.extern.log4j.Log4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Utilitaires pour extraire des champs simples (comme "text")
 * à partir d'une réponse GraphQL Weaviate.
 *
 * Structure visée :
 * {
 *   "data": {
 *     "Get": {
 *       "Expense": [
 *         { "text": "..." },
 *         { "text": "..." }
 *       ]
 *     }
 *   }
 * }
 */
@Slf4j
public final class WeaviateResponseParser {

    private WeaviateResponseParser() {
        // util class
    }

    /**
     * Extrait la liste des champs "text" d'une classe donnée dans la réponse GraphQL.
     *
     * @param response  réponse GraphQL Weaviate
     * @param className nom de la classe (ex: "Expense", "DocumentChunk", etc.)
     * @return liste des textes trouvés
     */
    @SuppressWarnings("unchecked")
    public static List<String> extractTexts(GraphQLResponse response, String className) {
        if (response == null || response.getData() == null) {
            return List.of();
        }

        Object dataObj = response.getData();
        if (!(dataObj instanceof Map<?, ?> dataMap)) {
            return List.of();
        }

        Object getObj = dataMap.get("Get");
        if (!(getObj instanceof Map<?, ?> getMap)) {
            return List.of();
        }

        Object classObj = getMap.get(className);
        if (!(classObj instanceof List<?> list)) {
            return List.of();
        }

        List<String> results = new ArrayList<>();

        for (Object o : list) {
            if (o instanceof Map<?, ?> objMap) {
                Object textVal = objMap.get("text");
                if (textVal != null) {
                    results.add(textVal.toString());
                }
            }
        }

        return results;
    }

    public static List<ExpenseItem> extractExpenseItems(GraphQLResponse response, String className) {
        if (response == null || response.getData() == null) {
            return List.of();
        }

        Object dataObj = response.getData();
        if (!(dataObj instanceof Map<?, ?> dataMap)) {
            return List.of();
        }

        Object getObj = dataMap.get("Get");
        if (!(getObj instanceof Map<?, ?> getMap)) {
            return List.of();
        }

        Object classObj = getMap.get(className);
        if (!(classObj instanceof List<?> list)) {
            return List.of();
        }

        List<ExpenseItem> result = new ArrayList<>();

        for (Object o : list) {
            if (o instanceof Map<?, ?> map) {
                ExpenseItem item = new ExpenseItem();
                
                Object id = map.get("expenseId");
                if (id == null) {
                    id = map.get("id"); // fallback legacy
                }
                if (id != null) {
                    try {
                        // Peut revenir en Double ; on cast proprement
                        item.setId((int) Math.round(Double.parseDouble(id.toString())));
                    } catch (Exception ignored) {
                        item.setId(0);
                    }
                } else {
                    item.setId(0);
                }
                Object amount = map.get("amount");
                if (amount != null) {
                    item.setAmount(Double.parseDouble(amount.toString()));
                }
                Object km = map.get("km");
                if (km != null) {
                    try { item.setKm(Double.parseDouble(km.toString())); } catch (Exception ignored) {}
                }
                Object currency = map.get("currency");
                if (currency != null) {
                    item.setCurrency(currency.toString());
                }
                Object type = map.get("type");
                if (type != null) {
                    item.setType(type.toString());
                }
                Object date = map.get("date");
                if (date != null) {
                    item.setDate(date.toString());
                }
                Object dateText = map.get("dateText");
                if (item.getDate() == null && dateText != null) {
                    item.setDate(dateText.toString());
                }
                Object description = map.get("description");
                if (description != null) {
                    item.setDescription(description.toString());
                }
                Object originalText = map.get("originalText");
                if (originalText != null) {
                    item.setOriginalText(originalText.toString());
                }
                Object paymentMode = map.get("paymentMode");
                if (paymentMode != null) {
                    item.setPaymentMode(paymentMode.toString());
                }
                Object address = map.get("address");
                if (address != null) {
                    item.setAddress(address.toString());
                }
                Object company = map.get("company");
                if (company != null) {
                    item.setCompany(company.toString());
                }
                log.info("extractExpenseItems: {} ",item);
                result.add(item);
            }
        }

        return result;
    }
}
