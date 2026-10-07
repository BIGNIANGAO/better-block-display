package blockreminder.smoke;

import com.evacipated.cardcrawl.modthespire.lib.*;
import com.megacrit.cardcrawl.integrations.DistributorFactory;
import com.megacrit.cardcrawl.integrations.PublisherIntegration;
import com.megacrit.cardcrawl.integrations.gog.GogIntegration;

/** Test-only: prevents achievements, cloud or account writes during smoke QA. */
@SpirePatch(clz = DistributorFactory.class, method = "getEnabledDistributor")
public final class OfflinePatch {
    @SpirePrefixPatch
    public static SpireReturn<PublisherIntegration> prefix(String distributor) {
        return SpireReturn.Return(new GogIntegration());
    }
}
