package com.gelakinetic.GathererScraper.JsonTypesGS;

import java.util.ArrayList;
import java.util.Collections;

import com.gelakinetic.GathererScraper.JsonTypes.Expansion;
import com.gelakinetic.GathererScraper.JsonTypes.Patch;

public class PatchGS extends Patch {

    /**
     * Create a patch from an expansion and a list of card.
     *
     * @param expansion the expansion this patch represents.
     * @param allCards 
     * @param cards     the collection of card this patch will represents.
     */
    public PatchGS(Expansion expansion, ArrayList<CardGS> allCards) {
        this.mExpansion = expansion;
        this.mCards = new ArrayList<>();
        this.mCards.addAll(allCards);
        Collections.sort(this.mCards);
    }
}
