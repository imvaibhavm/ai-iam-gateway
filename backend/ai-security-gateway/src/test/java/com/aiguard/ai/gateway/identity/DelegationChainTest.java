package com.aiguard.ai.gateway.identity;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

class DelegationChainTest {
    private final Instant now=Instant.parse("2026-08-19T00:00:00Z");
    @Test void validChainReducesScopes(){
        var chain=new DelegationChain("acme","vaibhav",List.of(
                grant("incident-agent","vaibhav","acme",Set.of("github.read","github.write"),1),
                grant("github-agent","incident-agent","acme",Set.of("github.read"),1)));
        assertThat(chain.validate(now).effectiveScopes()).containsExactly("github.read");
    }
    @Test void rejectsPrivilegeEscalation(){
        var chain=new DelegationChain("acme","vaibhav",List.of(
                grant("parent","vaibhav","acme",Set.of("github.read"),1),
                grant("child","parent","acme",Set.of("github.write"),1)));
        assertThat(chain.validate(now).reason()).isEqualTo("delegation_privilege_escalation");
    }
    @Test void rejectsExpiredAndCrossTenant(){
        assertThat(new DelegationChain("acme","v",List.of(grant("a","v","acme",Set.of(),-1))).validate(now).reason()).isEqualTo("delegation_expired");
        assertThat(new DelegationChain("acme","v",List.of(grant("a","v","other",Set.of(),1))).validate(now).reason()).isEqualTo("cross_tenant_delegation");
    }
    private DelegationChain.Grant grant(String subject,String by,String tenant,Set<String> scopes,long hours){return new DelegationChain.Grant(subject,by,tenant,scopes,now.plusSeconds(hours*3600));}
}
