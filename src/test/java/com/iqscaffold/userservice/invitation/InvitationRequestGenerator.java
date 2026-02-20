package com.iqscaffold.userservice.invitation;

import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;

/**
 * Generator for CreateInvitationRequest instances for property-based testing.
 * 
 * Generates valid invitation requests with:
 * - Random invitation types (EMAIL, LINK, CODE)
 * - Valid email addresses for EMAIL type
 * - Valid expiration hours (1-720)
 * - Valid max uses for LINK type
 * - Valid authority names
 */
public class InvitationRequestGenerator extends Generator<CreateInvitationRequest> {

  private static final String[] AUTHORITIES = {"USER"}; // Only USER for simplicity in property tests
  private static final InvitationType[] TYPES = InvitationType.values();

  public InvitationRequestGenerator() {
    super(CreateInvitationRequest.class);
  }

  @Override
  public CreateInvitationRequest generate(SourceOfRandomness random, GenerationStatus status) {
    InvitationType type = random.choose(TYPES);
    String email = type == InvitationType.EMAIL ? generateEmail(random) : null;
    String authority = random.choose(AUTHORITIES);
    int expirationHours = random.nextInt(1, 721); // 1-720 hours
    Integer maxUses = type == InvitationType.LINK && random.nextBoolean() 
        ? random.nextInt(1, 101) // 1-100 uses
        : null;

    return new CreateInvitationRequest(
        type,
        email,
        authority,
        expirationHours,
        maxUses,
        null // customMessage
    );
  }

  private String generateEmail(SourceOfRandomness random) {
    String[] domains = {"example.com", "test.com", "demo.com"};
    String username = "user" + random.nextInt(1, 10000);
    String domain = random.choose(domains);
    return username + "@" + domain;
  }
}
