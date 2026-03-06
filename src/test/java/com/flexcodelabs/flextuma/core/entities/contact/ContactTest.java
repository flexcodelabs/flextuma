package com.flexcodelabs.flextuma.core.entities.contact;

import com.flexcodelabs.flextuma.core.entities.metadata.ListEntity;
import com.flexcodelabs.flextuma.core.entities.metadata.Tag;
import com.flexcodelabs.flextuma.core.enums.StatusEnum;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ContactTest {

    @Test
    void testNoArgsConstructor() {
        Contact contact = new Contact();
        assertNotNull(contact.getLists());
        assertNotNull(contact.getTags());
        assertEquals(StatusEnum.ACTIVE, contact.getStatus());
    }

    @Test
    void testGettersAndSetters() {
        Contact contact = new Contact();
        contact.setFirstName("John");
        contact.setSurname("Doe");
        contact.setPhoneNumber("123456789");
        contact.setStatus(StatusEnum.INACTIVE);

        assertEquals("John", contact.getFirstName());
        assertEquals("Doe", contact.getSurname());
        assertEquals("123456789", contact.getPhoneNumber());
        assertEquals(StatusEnum.INACTIVE, contact.getStatus());
    }

    @Test
    void addToList_shouldUpdateBothSides() {
        Contact contact = new Contact();
        ListEntity list = mock(ListEntity.class);
        when(list.getContacts()).thenReturn(new ArrayList<>());

        contact.addToList(list);

        assertTrue(contact.getLists().contains(list));
        assertTrue(list.getContacts().contains(contact));
    }

    @Test
    void addToTag_shouldUpdateBothSides() {
        Contact contact = new Contact();
        Tag tag = mock(Tag.class);
        when(tag.getContacts()).thenReturn(new ArrayList<>());

        contact.addToTag(tag);

        assertTrue(contact.getTags().contains(tag));
        assertTrue(tag.getContacts().contains(contact));
    }

    @Test
    void onCreate_shouldSetDefaultStatus() {
        Contact contact = new Contact();
        contact.setStatus(null);

        contact.onCreate();

        assertEquals(StatusEnum.ACTIVE, contact.getStatus());
    }

    @Test
    void onCreate_withExistingStatus_shouldNotOverride() {
        Contact contact = new Contact();
        contact.setStatus(StatusEnum.INACTIVE);

        contact.onCreate();

        assertEquals(StatusEnum.INACTIVE, contact.getStatus());
    }
}
