package com.aiguard.ai.gateway.agent;

import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController @RequestMapping("/internal/agent")
public class InternalAgentController {
    private final AgentExecutionService service;
    public InternalAgentController(AgentExecutionService service){this.service=service;}
    @GetMapping("/runs/{id}/context") public Map<String,Object> context(@PathVariable String id){return service.context(id);}
    @PostMapping("/runs/{id}/inference") public Map<String,Object> inference(@PathVariable String id,@RequestBody InferenceRequest request){return Map.of("content",service.inference(id,request.prompt()));}
    @PostMapping("/runs/{id}/tools/propose") public Map<String,Object> propose(@PathVariable String id,@RequestBody ToolProposal request){return service.propose(id,request.toolName(),request.arguments());}
    @PostMapping("/approvals/{id}/execute") public Map<String,Object> execute(@PathVariable String id){return service.executeApproved(id);}
    public record InferenceRequest(String prompt){}
    public record ToolProposal(String toolName,Map<String,Object> arguments){}
}
