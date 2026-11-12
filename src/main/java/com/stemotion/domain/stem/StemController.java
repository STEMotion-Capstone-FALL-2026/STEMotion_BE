package com.stemotion.domain.stem;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import java.security.Principal;
import java.util.*;
@RestController
@RequestMapping("/api/v1")
@ConditionalOnProperty(name="app.stem.enabled",havingValue="true")
public class StemController {
 private final StemService service;private final StemValidator validator;
 public StemController(StemService s,StemValidator v){service=s;validator=v;}
 private String actor(Principal p){if(p==null)throw com.stemotion.core.exception.ApiException.unauthorized("Authentication required");return p.getName();}
 @GetMapping("/scripts/{project}/versions/{version}/stem")
 public StemDtos.View get(@PathVariable String project,@PathVariable String version,Principal p){return service.get(project,version,actor(p));}
 @PostMapping("/scripts/{project}/versions/{version}/stem")
 public StemDtos.View create(@PathVariable String project,@PathVariable String version,@RequestBody JsonNode body,Principal p){return service.create(project,version,actor(p),validator.parse(body,StemDtos.Create.class));}
 @PutMapping("/scripts/{project}/versions/{version}/stem")
 public StemDtos.View save(@PathVariable String project,@PathVariable String version,@RequestBody JsonNode body,Principal p){
  if(!body.path("brief").path("context").has("primary"))throw StemValidator.invalid("brief.context.primary","Explicit nullable primary required");
  return service.save(project,version,actor(p),validator.parse(body,StemDtos.Save.class));
 }
 @PostMapping("/scripts/{project}/versions/{version}/stem/validate")
 public StemDtos.View validate(@PathVariable String project,@PathVariable String version,@RequestBody JsonNode body,Principal p){return service.validate(project,version,actor(p),validator.parse(body,StemDtos.Validate.class));}
 @GetMapping("/workspaces/{workspace}/stem-settings")
 public StemModels.WorkspaceMode settings(@PathVariable String workspace,Principal p){return service.settings(workspace,actor(p));}
 @PutMapping("/workspaces/{workspace}/stem-settings")
 public StemModels.WorkspaceMode settings(@PathVariable String workspace,@RequestBody JsonNode body,Principal p){return service.saveSettings(workspace,actor(p),validator.parse(body,StemDtos.Settings.class));}
 @GetMapping("/stem/catalog")
 public List<StemModels.CatalogVersion> catalog(@RequestParam(required=false)String kind,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size,Principal p){return service.catalog(actor(p),kind,page,size,false);}
 @GetMapping("/stem/frameworks")
 public List<StemModels.CatalogVersion> frameworks(@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size,Principal p){return service.catalog(actor(p),null,page,size,true);}
 @PostMapping("/stem/catalog/import")
 public StemDtos.ImportResult importCatalog(@RequestBody JsonNode body,Principal p){return service.importCatalog(actor(p),body);}
}
