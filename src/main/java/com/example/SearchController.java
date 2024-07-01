package com.example;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/central")
public class SearchController {

    @Autowired
    private SearchService searchService;

    @GetMapping("/search")
    public Map<String, Object> search(@RequestParam int n) {
        return searchService.performDistributedSearch(n);
    }

    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        return searchService.getSystemStatus();
    }
}
