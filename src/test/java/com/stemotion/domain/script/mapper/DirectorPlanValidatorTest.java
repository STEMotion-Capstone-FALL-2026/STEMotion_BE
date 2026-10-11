package com.stemotion.domain.script.mapper;

import com.stemotion.core.exception.ApiException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DirectorPlanValidatorTest {
    private Map<String,Object> plan(String asset, String slot, Map<String,Object> props) {
        return Map.of("template_id","cosmic_zoom", "duration_sec",1.25, "narration","",
            "assets",List.of(Map.of("asset_id",asset,"slot",slot,"props",props)));
    }
    @Test void acceptsKnownPlanAndRejectsUnknownOrExecutableInput() {
        assertThatCode(() -> DirectorPlanValidator.validate(plan("glow_body","main",Map.of("color","#53eed7")))).doesNotThrowAnyException();
        for (var p : List.of(plan("generated_svg","main",Map.of()),plan("glow_body","unknown",Map.of()),
            plan("glow_body","main",Map.of("svg","<svg/>")),plan("glow_body","main",Map.of("color","url(https://example.com)")))) {
            assertThatThrownBy(() -> DirectorPlanValidator.validate(p)).isInstanceOf(ApiException.class);
        }
    }
    @Test void rejectsWrongTypesAndFrameBasedDirectorInput() {
        var p = new java.util.HashMap<>(plan("glow_body","main",Map.of()));
        p.put("durationInFrames",900);
        assertThatThrownBy(() -> DirectorPlanValidator.validate(p)).isInstanceOf(ApiException.class);
        p.remove("durationInFrames");p.put("duration_sec","30");
        assertThatThrownBy(() -> DirectorPlanValidator.validate(p)).isInstanceOf(ApiException.class);
    }
    @Test void phaseBAssetsAndVariantsMatchAllowlist() {
        for (String id : List.of("force_vector","orbit_path","pendulum","lens","wave","atom","molecule","flask","element_tile")) {
            var p = new java.util.HashMap<>(plan(id,"main",Map.of()));
            p.put("layout_variant","left");
            assertThatCode(() -> DirectorPlanValidator.validate(p)).doesNotThrowAnyException();
            p.put("layout_variant","unknown");
            assertThatThrownBy(() -> DirectorPlanValidator.validate(p)).isInstanceOf(ApiException.class);
        }
        assertThatThrownBy(() -> DirectorPlanValidator.validate(plan("atom","main",Map.of("electrons",2.5)))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> DirectorPlanValidator.validate(plan("lens","main",Map.of("focalLength",999)))).isInstanceOf(ApiException.class);
    }
    @Test void rejectsUnverifiedChemistryPresets() {
        for (var p : List.of(plan("atom","main",Map.of("protons",7)),
            plan("atom","main",Map.of("electrons",5)),
            plan("element_tile","main",Map.of("symbol","O")),
            plan("element_tile","main",Map.of("mass",14)),
            plan("molecule","main",Map.of("formula","carbon_dioxide")))) {
            assertThatThrownBy(() -> DirectorPlanValidator.validate(p)).isInstanceOf(ApiException.class);
        }
    }
}
