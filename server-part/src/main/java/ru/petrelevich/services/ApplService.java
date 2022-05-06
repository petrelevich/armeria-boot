package ru.petrelevich.services;

import org.springframework.stereotype.Service;
import ru.petrelevich.armeria.GetMapping;
import ru.petrelevich.armeria.PathVariable;
import ru.petrelevich.armeria.RequestMapping;
import ru.petrelevich.armeria.RequestParam;

import java.util.Arrays;
import java.util.List;

@Service
@RequestMapping("${application.rest.api.prefix}/v1")
public class ApplService {

    @GetMapping("/param/{name}/{id}")
    public List<String> process(@PathVariable("name") String name,
                                @PathVariable("id") int id,
                                @RequestParam("gender") String gender) {
        return Arrays.asList(name, String.valueOf(id), gender);
    }
}
