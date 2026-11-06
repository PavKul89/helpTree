package org.example.helptreeservice.mapper;

import org.example.helptreeservice.entity.Help;
import org.example.helptreeservice.entity.Post;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.enums.HelpActorRole;
import org.example.helptreeservice.enums.HelpStatus;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HelpMapperTest {

    private final HelpMapper mapper = Mappers.getMapper(HelpMapper.class);

    @Test
    void publicProjectionOmitsCancellationAuditFields() {
        Help help = cancelledHelp();

        var response = mapper.toPublicResponse(help);

        assertEquals(1L, response.getId());
        assertEquals(10L, response.getPostId());
        assertEquals("Помощь с продуктами", response.getPostTitle());
        assertEquals(2L, response.getHelperId());
        assertEquals("Helper", response.getHelperName());
        assertEquals(3L, response.getReceiverId());
        assertEquals("Receiver", response.getReceiverName());
        assertEquals("CANCELLED", response.getStatus());
        assertNull(response.getCancelReason());
        assertNull(response.getCancelledById());
        assertNull(response.getCancelInitiator());
    }

    @Test
    void participantProjectionKeepsCancellationAuditFields() {
        Help help = cancelledHelp();

        var response = mapper.toResponse(help);

        assertEquals("Не смогу", response.getCancelReason());
        assertEquals(2L, response.getCancelledById());
        assertEquals("HELPER", response.getCancelInitiator());
    }

    private Help cancelledHelp() {
        User helper = new User();
        helper.setId(2L);
        helper.setName("Helper");

        User receiver = new User();
        receiver.setId(3L);
        receiver.setName("Receiver");

        Post post = new Post();
        post.setId(10L);
        post.setTitle("Помощь с продуктами");

        Help help = new Help();
        help.setId(1L);
        help.setPost(post);
        help.setHelper(helper);
        help.setReceiver(receiver);
        help.setStatus(HelpStatus.CANCELLED);
        help.setCancelReason("Не смогу");
        help.setCancelledById(2L);
        help.setCancelInitiator(HelpActorRole.HELPER);
        return help;
    }
}
