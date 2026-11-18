package com.valier.client;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import net.minecraft.client.resources.Language;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import codechicken.nei.ItemList;
import codechicken.nei.SearchField;
import codechicken.nei.SearchTokenParser;
import codechicken.nei.api.ItemFilter;

/**
 * NEI's "@" search (by mod name) finds a Valier XP Crafter under Valier Tweaks too. NEI files every item under the mod
 * whose item it is, and the crafter is a GregTech tank item, so "@Valier" found nothing. NEI uses one provider per
 * prefix (the first one listed), so this wraps NEI's own "@" provider in place: what it finds is still found, and a
 * crafter is found as well when the text matches "Valier Tweaks". Players' computers only, from
 * {@link NEIValierClientConfig} (NEI adds its "@" provider at its first world load, just before it loads plugins).
 */
final class ModSearch implements SearchTokenParser.ISearchParserProvider {

    static final Logger LOG = LogManager.getLogger("ValierClient");
    static final char PREFIX = '@';

    final SearchTokenParser.ISearchParserProvider nei;

    ModSearch(SearchTokenParser.ISearchParserProvider nei) {
        this.nei = nei;
    }

    /** Puts the wrapper in place of NEI's "@" provider; false (and a log line) if it isn't there. */
    @SuppressWarnings("unchecked")
    static boolean install() {
        SearchTokenParser parser = SearchField.searchParser;
        try {
            Field f = SearchTokenParser.class.getDeclaredField("searchProviders");
            f.setAccessible(true);
            List<SearchTokenParser.ISearchParserProvider> list = (List<SearchTokenParser.ISearchParserProvider>) f.get(parser);
            synchronized (parser) {
                for (int i = 0; i < list.size(); i++) {
                    SearchTokenParser.ISearchParserProvider p = list.get(i);
                    if (p instanceof ModSearch) return true;
                    if (p.getPrefix() != PREFIX) continue;
                    list.set(i, new ModSearch(p));
                    parser.clearCache();
                    return true;
                }
            }
            LOG.warn("NEI has no \"@\" search; \"@Valier\" won't find the Valier XP Crafter");
        } catch (Throwable e) {
            LOG.warn("Couldn't add the Valier XP Crafter to NEI's \"@\" search", e);
        }
        return false;
    }

    @Override
    public ItemFilter getFilter(String text) {
        ItemFilter base = nei.getFilter(text);
        Pattern pattern = SearchField.getPattern(text);
        if (pattern == null) return base;
        String mod = StatCollector.translateToLocal("valierclient.xpcrafter.mod");
        ItemFilter crafter = stack -> ModNameLine.isCrafter(stack) && pattern.matcher(mod)
            .find();
        if (base == null) return crafter;
        List<ItemFilter> both = new ArrayList<>();
        both.add(base);
        both.add(crafter);
        return new ItemList.AnyMultiItemFilter(both);
    }

    @Override
    public char getPrefix() {
        return nei.getPrefix();
    }

    @Override
    public EnumChatFormatting getHighlightedColor() {
        return nei.getHighlightedColor();
    }

    @Override
    public SearchTokenParser.SearchMode getSearchMode() {
        return nei.getSearchMode();
    }

    @Override
    public String getName() {
        return nei.getName();
    }

    @Override
    public List<Language> getMatchingLanguages() {
        return nei.getMatchingLanguages();
    }
}
