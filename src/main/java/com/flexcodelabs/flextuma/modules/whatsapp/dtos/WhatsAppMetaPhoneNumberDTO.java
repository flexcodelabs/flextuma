package com.flexcodelabs.flextuma.modules.whatsapp.dtos;

/** A phone number as Meta reports it (GET /{phone-number-id}). */
public record WhatsAppMetaPhoneNumberDTO(
        String id,
        String displayPhoneNumber,
        String verifiedName,
        String qualityRating,
        String codeVerificationStatus,
        String nameStatus,
        String platformType) {
}
