package com.example.dockertest.controller;

import com.example.dockertest.model.DeploymentTask;
import com.example.dockertest.repository.DeploymentTaskRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.List;

@Controller
public class DeploymentTaskController {

    private final DeploymentTaskRepository repository;

    @Autowired
    public DeploymentTaskController(DeploymentTaskRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/")
    public String index(Model model) {
        List<DeploymentTask> tasks = repository.findAll();
        
        // Pre-populate with sample deployments if the database is empty
        if (tasks.isEmpty()) {
            repository.save(new DeploymentTask("Cloud Deploy Hub", "Production", 8080, "Running", "This Spring Boot Thymeleaf & Postgres application."));
            repository.save(new DeploymentTask("User Authentication Service", "Staging", 8081, "Running", "Microservice for managing user sessions and OAuth2."));
            repository.save(new DeploymentTask("Neon PostgreSQL", "Production", 5432, "Running", "Live serverless cloud database hosted on Neon."));
            tasks = repository.findAll();
        }
        
        model.addAttribute("tasks", tasks);
        model.addAttribute("newTask", new DeploymentTask());
        return "index";
    }

    @PostMapping("/add")
    public String addDeployment(@ModelAttribute("newTask") DeploymentTask newTask) {
        if (newTask.getServiceName() != null && !newTask.getServiceName().trim().isEmpty()) {
            if (newTask.getStatus() == null || newTask.getStatus().isEmpty()) {
                newTask.setStatus("Running");
            }
            repository.save(newTask);
        }
        return "redirect:/";
    }

    @PostMapping("/edit")
    public String editDeployment(@ModelAttribute("editTask") DeploymentTask editTask) {
        if (editTask.getId() != null && editTask.getServiceName() != null && !editTask.getServiceName().trim().isEmpty()) {
            repository.save(editTask);
        }
        return "redirect:/";
    }

    @PostMapping("/delete/{id}")
    public String deleteDeployment(@PathVariable("id") Long id) {
        repository.deleteById(id);
        return "redirect:/";
    }
}
