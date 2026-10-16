package com.dev.control_financiero.service;

import com.dev.control_financiero.entity.Categoria;
import com.dev.control_financiero.repository.CategoriaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/** Conservative merchant rules, never product inference or classification of a generic purchase. */
@Service @RequiredArgsConstructor
public class MerchantCategoryService {
    private final CategoriaRepository categories;
    private record Rule(String name, Set<String> aliases, Pattern merchant) {}
    private static Rule rule(String name, String aliases, String expression) {
        return new Rule(name, new HashSet<>(Arrays.asList(aliases.split("\\|"))), Pattern.compile("\\b(?:"+expression+")\\b"));
    }
    private static final List<Rule> RULES=List.of(
        rule("Comida", "COMIDA|ALIMENTACION", "LACA LACA|RESTAURANTE(?:S)?|CAFETERIA(?:S)?|PIZZERIA(?:S)?|POLLO CAMPERO|PIZZA HUT|BURGER KING|MCDONALD(?: S)?|WENDY(?: S)?"),
        rule("Supermercado", "SUPERMERCADO|SUPERMERCADOS", "MAXI DESPENSA|SUPER SELECTOS|SELECTOS|WALMART|DESPENSA DE DON JUAN"),
        rule("Combustible", "COMBUSTIBLE|GASOLINA", "GASOLINERA(?:S)?|PUMA ENERGY|TEXACO|SHELL"),
        rule("Salud", "SALUD|FARMACIA|FARMACIAS", "FARMACIA(?:S)?")
    );
    private static String normalize(String text) {
        return Normalizer.normalize(text==null?"":text,Normalizer.Form.NFD).replaceAll("\\p{M}+","")
                .toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+"," ").trim();
    }
    public String suggestedName(String merchant) {
        String value=normalize(merchant);
        var matches=RULES.stream().filter(r->r.merchant().matcher(value).find()).toList();
        return matches.size()==1?matches.get(0).name():null;
    }
    // Called inside the importer's transaction; category and movement commit together.
    public synchronized Long categoryId(String merchant) {
        String name=suggestedName(merchant);
        if(name==null) return null;
        Rule rule=RULES.stream().filter(r->r.name().equals(name)).findFirst().orElseThrow();
        var existing=categories.findAll();
        var exact=existing.stream().filter(c->normalize(c.getNombre()).equals(normalize(name))).findFirst();
        if(exact.isPresent()) return Boolean.TRUE.equals(exact.get().getActiva())?exact.get().getId():null;
        var aliases=existing.stream().filter(c->Boolean.TRUE.equals(c.getActiva())&&rule.aliases().contains(normalize(c.getNombre()))).toList();
        if(aliases.size()==1) return aliases.get(0).getId();
        if(aliases.size()>1) return null;
        if(existing.stream().anyMatch(c->rule.aliases().contains(normalize(c.getNombre())))) return null;
        return categories.save(Categoria.builder().nombre(name).activa(true).build()).getId();
    }
}
