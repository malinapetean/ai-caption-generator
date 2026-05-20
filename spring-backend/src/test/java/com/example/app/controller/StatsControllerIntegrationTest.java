package com.example.app.controller;

import java.util.UUID;

import com.example.app.model.AppUser;
import com.example.app.model.Caption;
import com.example.app.model.ImageRecord;
import com.example.app.repository.AppUserRepository;
import com.example.app.repository.CaptionRepository;
import com.example.app.repository.ImageRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class StatsControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private ImageRecordRepository imageRecordRepository;

    @Autowired
    private CaptionRepository captionRepository;

    @BeforeEach
    void setUp() {
        captionRepository.deleteAll();
        imageRecordRepository.deleteAll();
        appUserRepository.deleteAll();
    }

    @Test
    void getStats_returnsOnlyAuthenticatedUserAggregates() throws Exception {
        AppUser authenticatedUser = saveUser("travel");
        AppUser otherUser = saveUser("luxury");

        ImageRecord firstImage = saveImage(authenticatedUser, "user-1/first.jpg");
        ImageRecord secondImage = saveImage(authenticatedUser, "user-1/second.jpg");
        ImageRecord otherImage = saveImage(otherUser, "user-2/other.jpg");

        saveCaption(firstImage, "Casual", false, "Casual draft");
        saveCaption(firstImage, "casual", true, "Casual winner");
        saveCaption(secondImage, " Minimalist ", true, "Minimalist winner");
        saveCaption(secondImage, "minimalist", true, "Minimalist encore");
        saveCaption(secondImage, "   ", false, "Blank style");

        saveCaption(otherImage, "luxury", true, "Other user caption");

        mockMvc.perform(get("/api/stats").with(user(authenticatedUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCaptions").value(5))
                .andExpect(jsonPath("$.totalSelectedCaptions").value(3))
                .andExpect(jsonPath("$.totalUploadedImages").value(2))
                .andExpect(jsonPath("$.mostUsedStyle").value("casual"))
                .andExpect(jsonPath("$.mostSelectedStyle").value("minimalist"))
                .andExpect(jsonPath("$.preferredStyle").value("minimalist"))
                .andExpect(jsonPath("$.generatedByStyle.length()").value(3))
                .andExpect(jsonPath("$.generatedByStyle[0].style").value("casual"))
                .andExpect(jsonPath("$.generatedByStyle[0].count").value(2))
                .andExpect(jsonPath("$.generatedByStyle[0].percentage").value(closeTo(40.0, 0.0001)))
                .andExpect(jsonPath("$.generatedByStyle[1].style").value("minimalist"))
                .andExpect(jsonPath("$.generatedByStyle[1].count").value(2))
                .andExpect(jsonPath("$.generatedByStyle[1].percentage").value(closeTo(40.0, 0.0001)))
                .andExpect(jsonPath("$.generatedByStyle[2].style").value("unknown"))
                .andExpect(jsonPath("$.generatedByStyle[2].count").value(1))
                .andExpect(jsonPath("$.generatedByStyle[2].percentage").value(closeTo(20.0, 0.0001)))
                .andExpect(jsonPath("$.selectedByStyle.length()").value(2))
                .andExpect(jsonPath("$.selectedByStyle[0].style").value("minimalist"))
                .andExpect(jsonPath("$.selectedByStyle[0].count").value(2))
                .andExpect(jsonPath("$.selectedByStyle[0].percentage").value(closeTo(66.66666666666667, 0.0001)))
                .andExpect(jsonPath("$.selectedByStyle[1].style").value("casual"))
                .andExpect(jsonPath("$.selectedByStyle[1].count").value(1))
                .andExpect(jsonPath("$.selectedByStyle[1].percentage").value(closeTo(33.333333333333336, 0.0001)))
                .andExpect(jsonPath("$.selectionRateByStyle.length()").value(3))
                .andExpect(jsonPath("$.selectionRateByStyle[0].style").value("minimalist"))
                .andExpect(jsonPath("$.selectionRateByStyle[0].generatedCount").value(2))
                .andExpect(jsonPath("$.selectionRateByStyle[0].selectedCount").value(2))
                .andExpect(jsonPath("$.selectionRateByStyle[0].selectionRate").value(closeTo(100.0, 0.0001)))
                .andExpect(jsonPath("$.selectionRateByStyle[1].style").value("casual"))
                .andExpect(jsonPath("$.selectionRateByStyle[1].generatedCount").value(2))
                .andExpect(jsonPath("$.selectionRateByStyle[1].selectedCount").value(1))
                .andExpect(jsonPath("$.selectionRateByStyle[1].selectionRate").value(closeTo(50.0, 0.0001)))
                .andExpect(jsonPath("$.selectionRateByStyle[2].style").value("unknown"))
                .andExpect(jsonPath("$.selectionRateByStyle[2].generatedCount").value(1))
                .andExpect(jsonPath("$.selectionRateByStyle[2].selectedCount").value(0))
                .andExpect(jsonPath("$.selectionRateByStyle[2].selectionRate").value(closeTo(0.0, 0.0001)));
    }

    @Test
    void getStats_withNoData_returnsZeroedResponse() throws Exception {
        AppUser authenticatedUser = saveUser("casual");

        mockMvc.perform(get("/api/stats").with(user(authenticatedUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCaptions").value(0))
                .andExpect(jsonPath("$.totalSelectedCaptions").value(0))
                .andExpect(jsonPath("$.totalUploadedImages").value(0))
                .andExpect(jsonPath("$.mostUsedStyle").value(nullValue()))
                .andExpect(jsonPath("$.mostSelectedStyle").value(nullValue()))
                .andExpect(jsonPath("$.preferredStyle").value(nullValue()))
                .andExpect(jsonPath("$.generatedByStyle.length()").value(0))
                .andExpect(jsonPath("$.selectedByStyle.length()").value(0))
                .andExpect(jsonPath("$.selectionRateByStyle.length()").value(0));
    }

    private AppUser saveUser(String preferredStyle) {
        AppUser user = new AppUser();
        user.setEmail("stats-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash("password-hash");
        user.setPreferredStyle(preferredStyle);
        return appUserRepository.save(user);
    }

    private ImageRecord saveImage(AppUser user, String path) {
        ImageRecord imageRecord = new ImageRecord();
        imageRecord.setUser(user);
        imageRecord.setPath(path);
        return imageRecordRepository.save(imageRecord);
    }

    private Caption saveCaption(ImageRecord imageRecord, String style, boolean selected, String text) {
        Caption caption = new Caption();
        caption.setImage(imageRecord);
        caption.setStyle(style);
        caption.setGenerationBatchId(UUID.randomUUID().toString());
        caption.setSelected(selected);
        caption.setText(text);
        return captionRepository.save(caption);
    }
}
