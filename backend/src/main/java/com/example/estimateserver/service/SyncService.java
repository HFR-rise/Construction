package com.example.estimateserver.service;

import com.example.estimateserver.dto.SyncMessage;
import com.example.estimateserver.model.ShareStatus;
import com.example.estimateserver.model.*;
import com.example.estimateserver.repository.*;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SyncService {

    private final ProjectRepository projectRepository;
    private final MaterialRepository materialRepository;
    private final WorkItemRepository workItemRepository;
    private final ContactRepository contactRepository;
    private final ContactMethodRepository contactMethodRepository;
    private final ObjectRepository objectRepository;
    private final UserRepository userRepository;
    private final SharedProjectRepository sharedProjectRepository;
    private final WebSocketService webSocketService;

    public SyncService(ProjectRepository projectRepository,
                       MaterialRepository materialRepository,
                       WorkItemRepository workItemRepository,
                       ContactRepository contactRepository,
                       ContactMethodRepository contactMethodRepository,
                       ObjectRepository objectRepository,
                       UserRepository userRepository,
                       SharedProjectRepository sharedProjectRepository,
                       WebSocketService webSocketService) {
        this.projectRepository = projectRepository;
        this.materialRepository = materialRepository;
        this.workItemRepository = workItemRepository;
        this.contactRepository = contactRepository;
        this.contactMethodRepository = contactMethodRepository;
        this.objectRepository = objectRepository;
        this.userRepository = userRepository;
        this.sharedProjectRepository = sharedProjectRepository;
        this.webSocketService = webSocketService;
    }


    @Transactional
    public Project createProject(Project project, String userId) {
        project.setUserId(userId);
        project.setOwnerId(userId);
        project.setCreatedBy(userId);
        project.setCreatedAt(new Date());
        project.setUpdatedAt(new Date());
        project.setLastModifiedBy(userId);

        Project saved = projectRepository.save(project);

        System.out.println("=== CREATE PROJECT ===");
        System.out.println("Project ID: " + saved.getId());
        System.out.println("User ID: " + userId);
        System.out.println("Project userId: " + saved.getUserId());

        SyncMessage message = new SyncMessage(
                "CREATE", "PROJECT", saved.getId(), saved,
                userId, new Date(), saved.getVersion()
        );
        webSocketService.sendToUser(userId, message);

        shareProjectWithContacts(saved, userId);

        return saved;
    }

    @Transactional
    public Project updateProject(Project project, String userId) {
        Optional<Project> existing = projectRepository.findById(project.getId());
        if (existing.isEmpty()) {
            System.out.println("=== PROJECT NOT FOUND, CREATING NEW ===");
            return createProject(project, userId);
        }

        Project oldProject = existing.get();

        System.out.println("=== UPDATE PROJECT ===");
        System.out.println("Project ID: " + project.getId());
        System.out.println("Project Name: " + project.getName());
        System.out.println("OLD objectId: '" + oldProject.getObjectId() + "'");
        System.out.println("NEW objectId: '" + project.getObjectId() + "'");
        System.out.println("Header userId: " + userId);
        System.out.println("Existing project.userId: " + oldProject.getUserId());

        project.setUpdatedAt(new Date());
        project.setLastModifiedBy(userId);
        project.setVersion(oldProject.getVersion());
        project.setOwnerId(oldProject.getOwnerId());
        project.setUserId(oldProject.getUserId());
        project.setCreatedAt(oldProject.getCreatedAt());
        project.setCreatedBy(oldProject.getCreatedBy());

        Project saved = projectRepository.save(project);

        System.out.println("SAVED objectId: '" + saved.getObjectId() + "'");
        System.out.println("Saved project.userId: " + saved.getUserId());

        notifyProjectParticipants(saved.getId(), "UPDATE", "PROJECT", saved, userId);
        notifyNewContacts(oldProject, saved, userId);

        return saved;
    }

    @Transactional
    public void deleteProject(String projectId, String userId) {
        Project project = projectRepository.findById(projectId).orElse(null);
        if (project == null) return;
        if (!project.getUserId().equals(userId)) {
            throw new RuntimeException("Only owner can delete project");
        }

        List<String> affectedUserIds = findUsersWithAccessToProject(projectId);
        projectRepository.deleteById(projectId);

        List<SharedProject> shares = sharedProjectRepository.findByProjectId(projectId);
        sharedProjectRepository.deleteAll(shares);

        for (String affectedUserId : affectedUserIds) {
            SyncMessage message = new SyncMessage(
                    "DELETE", "PROJECT", projectId, null,
                    userId, new Date(), null
            );
            webSocketService.sendToUser(affectedUserId, message);
        }
    }

    public List<Project> getProjects() {
        return projectRepository.findAll();
    }

    public Optional<Project> getProject(String id) {
        return projectRepository.findById(id);
    }

    public List<Project> getProjectsForUser(String userId) {
        Set<Project> allProjects = new HashSet<>();

        List<Project> ownedProjects = projectRepository.findByUserId(userId);
        allProjects.addAll(ownedProjects);

        List<String> sharedProjectIds = sharedProjectRepository.findSharedProjectIdsByUser(userId);
        if (!sharedProjectIds.isEmpty()) {
            List<Project> sharedProjects = projectRepository.findAllById(sharedProjectIds);
            allProjects.addAll(sharedProjects);
        }

        return new ArrayList<>(allProjects);
    }


    @Transactional
    public void shareProjectWithUser(String projectId, String targetUserPhone, String sharedByUserId) {
        System.out.println("=== SHARING PROJECT WITH COPY ===");
        System.out.println("Project ID: " + projectId);
        System.out.println("Target phone: " + targetUserPhone);
        System.out.println("Shared by user: " + sharedByUserId);

        Project originalProject = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));

        if (!originalProject.getUserId().equals(sharedByUserId)) {
            throw new RuntimeException("You can only share your own projects");
        }

        User targetUser = userRepository.findByPhoneNumber(targetUserPhone)
                .orElseThrow(() -> new RuntimeException("User not found"));



        Project sharedProjectCopy = new Project();
        sharedProjectCopy.setName(originalProject.getName());
        sharedProjectCopy.setDescription(originalProject.getDescription());
        sharedProjectCopy.setObjectId(null);
        sharedProjectCopy.setUserId(targetUser.getId());
        sharedProjectCopy.setOwnerId(targetUser.getId());
        sharedProjectCopy.setCreatedBy(sharedByUserId);
        sharedProjectCopy.setCreatedAt(new Date());
        sharedProjectCopy.setUpdatedAt(new Date());
        sharedProjectCopy.setStatus("ACTIVE");
        sharedProjectCopy.setTotalBudget(originalProject.getTotalBudget());
        sharedProjectCopy.setTotalSpent(0.0);

        sharedProjectCopy.setCustomerContactId(originalProject.getCustomerContactId());
        sharedProjectCopy.setForemanContactId(originalProject.getForemanContactId());
        sharedProjectCopy.setManagerContactId(originalProject.getManagerContactId());
        sharedProjectCopy.setIncludeForeman(originalProject.isIncludeForeman());
        sharedProjectCopy.setIncludeManager(originalProject.isIncludeManager());

        Project savedCopy = projectRepository.save(sharedProjectCopy);
        System.out.println("✅ Created NEW copy of project with ID: " + savedCopy.getId());

        List<Material> materials = materialRepository.findByProjectId(projectId);
        for (Material material : materials) {
            Material materialCopy = new Material();
            materialCopy.setProjectId(savedCopy.getId());
            materialCopy.setName(material.getName());
            materialCopy.setQuantity(material.getQuantity());
            materialCopy.setUnit(material.getUnit());
            materialCopy.setUnitPrice(material.getUnitPrice());
            materialCopy.setCategory(material.getCategory());
            materialCopy.setNotes(material.getNotes());
            materialCopy.setUserId(targetUser.getId());
            materialRepository.save(materialCopy);
        }
        System.out.println("✅ Copied " + materials.size() + " materials");

        List<WorkItem> workItems = workItemRepository.findByProjectId(projectId);
        for (WorkItem workItem : workItems) {
            WorkItem workCopy = new WorkItem();
            workCopy.setProjectId(savedCopy.getId());
            workCopy.setName(workItem.getName());
            workCopy.setStage(workItem.getStage());
            workCopy.setLaborHours(workItem.getLaborHours());
            workCopy.setHourlyRate(workItem.getHourlyRate());
            workCopy.setMaterialCost(workItem.getMaterialCost());
            workCopy.setNotes(workItem.getNotes());
            workCopy.setUserId(targetUser.getId());
            workItemRepository.save(workCopy);
        }
        System.out.println("✅ Copied " + workItems.size() + " work items");

        SharedProject shared = new SharedProject(projectId, targetUser.getId(), sharedByUserId, "READ");
        shared.setStatus(ShareStatus.ACCEPTED);
        shared.setRespondedAt(new Date());
        sharedProjectRepository.save(shared);

        SyncMessage message = new SyncMessage(
                "CREATE",
                "PROJECT",
                savedCopy.getId(),
                savedCopy,
                sharedByUserId,
                new Date(),
                0L
        );
        webSocketService.sendToUser(targetUser.getId(), message);

        System.out.println("📤 Sent WebSocket notification to user: " + targetUser.getId());
        System.out.println("=== SHARING COMPLETED ===");
    }


    public List<Project> getProjectsSharedWithUser(String userId) {
        List<String> sharedProjectIds = sharedProjectRepository.findSharedProjectIdsByUser(userId);
        if (sharedProjectIds.isEmpty()) return new ArrayList<>();
        return projectRepository.findAllById(sharedProjectIds);
    }


    @Transactional
    public Material addMaterial(Material material, String userId) {
        if (!hasAccessToProject(material.getProjectId(), userId)) {
            throw new RuntimeException("Access denied");
        }

        material.setUserId(userId);
        Material saved = materialRepository.save(material);

        notifyProjectParticipants(saved.getProjectId(), "CREATE", "MATERIAL", saved, userId);
        updateProjectTotal(saved.getProjectId());
        return saved;
    }

    @Transactional
    public Material updateMaterial(Material material, String userId) {
        if (!hasAccessToProject(material.getProjectId(), userId)) {
            throw new RuntimeException("Access denied");
        }

        Material saved = materialRepository.save(material);

        notifyProjectParticipants(saved.getProjectId(), "UPDATE", "MATERIAL", saved, userId);
        updateProjectTotal(saved.getProjectId());
        return saved;
    }

    @Transactional
    public void deleteMaterial(String materialId, String userId) {
        Optional<Material> material = materialRepository.findById(materialId);
        if (material.isPresent()) {
            String projectId = material.get().getProjectId();

            if (!hasAccessToProject(projectId, userId)) {
                throw new RuntimeException("Access denied");
            }

            materialRepository.deleteById(materialId);
            notifyProjectParticipants(projectId, "DELETE", "MATERIAL", materialId, userId);
            updateProjectTotal(projectId);
        }
    }

    public List<Material> getMaterials(String projectId) {
        return materialRepository.findByProjectId(projectId);
    }


    public WorkItem getWorkItemById(String id) {
        return workItemRepository.findById(id).orElse(null);
    }

    @Transactional
    public WorkItem addWorkItem(WorkItem workItem, String userId) {
        if (!hasAccessToProject(workItem.getProjectId(), userId)) {
            throw new RuntimeException("Access denied");
        }

        workItem.setUserId(userId);
        WorkItem saved = workItemRepository.save(workItem);

        notifyProjectParticipants(saved.getProjectId(), "CREATE", "WORK_ITEM", saved, userId);
        updateProjectTotal(saved.getProjectId());
        return saved;
    }

    @Transactional
    public WorkItem updateWorkItem(WorkItem workItem, String userId) {
        if (!hasAccessToProject(workItem.getProjectId(), userId)) {
            throw new RuntimeException("Access denied");
        }

        WorkItem saved = workItemRepository.save(workItem);

        notifyProjectParticipants(saved.getProjectId(), "UPDATE", "WORK_ITEM", saved, userId);
        updateProjectTotal(saved.getProjectId());
        return saved;
    }

    @Transactional
    public void deleteWorkItem(String workItemId, String userId) {
        Optional<WorkItem> workItem = workItemRepository.findById(workItemId);
        if (workItem.isPresent()) {
            String projectId = workItem.get().getProjectId();

            if (!hasAccessToProject(projectId, userId)) {
                throw new RuntimeException("Access denied");
            }

            workItemRepository.deleteById(workItemId);
            notifyProjectParticipants(projectId, "DELETE", "WORK_ITEM", workItemId, userId);
            updateProjectTotal(projectId);
        }
    }

    public List<WorkItem> getWorkItems(String projectId) {
        return workItemRepository.findByProjectId(projectId);
    }

    @Transactional
    public WorkItem markWorkItemAsCompleted(String workItemId, String userId) {
        Optional<WorkItem> existing = workItemRepository.findById(workItemId);
        if (existing.isPresent()) {
            WorkItem workItem = existing.get();

            if (!hasAccessToProject(workItem.getProjectId(), userId)) {
                throw new RuntimeException("Access denied");
            }

            workItem.setIsCompleted(true);
            WorkItem saved = workItemRepository.save(workItem);

            notifyProjectParticipants(saved.getProjectId(), "UPDATE", "WORK_ITEM", saved, userId);
            updateProjectTotal(saved.getProjectId());
            return saved;
        }
        return null;
    }


    public List<Contact> searchContactsByPhoneForUser(String phone, String userId) {
        String normalizedPhone = normalizePhoneNumber(phone);
        if (normalizedPhone == null || normalizedPhone.isEmpty()) {
            return new ArrayList<>();
        }
        return contactRepository.findByUserIdAndPhoneNumber(userId, normalizedPhone);
    }

    @Transactional
    public Contact addContact(Contact contact, String userId) {
        contact.setUserId(userId);
        Contact saved = contactRepository.save(contact);

        System.out.println("=== CONTACT CREATED ===");
        System.out.println("Contact ID: " + saved.getId());
        System.out.println("Contact name: " + saved.getName());
        System.out.println("User ID: " + userId);

        SyncMessage message = new SyncMessage(
                "CREATE", "CONTACT", saved.getId(), saved,
                userId, new Date(), null
        );
        webSocketService.sendToUser(userId, message);

        return saved;
    }

    @Transactional
    public Contact updateContact(Contact contact, String userId) {
        Contact existing = contactRepository.findById(contact.getId()).orElse(null);
        if (existing == null || !existing.getUserId().equals(userId)) {
            throw new RuntimeException("Access denied");
        }

        contact.setUserId(userId);
        Contact saved = contactRepository.save(contact);

        SyncMessage message = new SyncMessage(
                "UPDATE", "CONTACT", saved.getId(), saved,
                userId, new Date(), null
        );
        webSocketService.sendToUser(userId, message);

        return saved;
    }

    @Transactional
    public void deleteContact(String contactId, String userId) {
        Contact contact = contactRepository.findById(contactId).orElse(null);
        if (contact == null || !contact.getUserId().equals(userId)) {
            throw new RuntimeException("Access denied");
        }

        List<ContactMethod> methods = contactMethodRepository.findByContactId(contactId);
        contactMethodRepository.deleteAll(methods);
        contactRepository.deleteById(contactId);

        System.out.println("=== CONTACT DELETED ===");
        System.out.println("Contact ID: " + contactId);
        System.out.println("User ID: " + userId);

        SyncMessage message = new SyncMessage(
                "DELETE", "CONTACT", contactId, null,
                userId, new Date(), null
        );
        webSocketService.sendToUser(userId, message);
    }

    public List<Contact> getContactsForUser(String userId) {
        return contactRepository.findByUserId(userId);
    }


    @Transactional
    public ContactMethod addContactMethod(ContactMethod method, String userId) {
        Contact contact = contactRepository.findById(method.getContactId()).orElse(null);
        if (contact == null || !contact.getUserId().equals(userId)) {
            throw new RuntimeException("Access denied");
        }

        method.setUserId(userId);
        ContactMethod saved = contactMethodRepository.save(method);

        SyncMessage message = new SyncMessage(
                "CREATE", "CONTACT_METHOD", saved.getId(), saved,
                userId, new Date(), null
        );
        webSocketService.sendToUser(userId, message);

        return saved;
    }

    public List<ContactMethod> getContactMethods(String contactId) {
        return contactMethodRepository.findByContactId(contactId);
    }


    public List<ObjectModel> getRootObjectsForUser(String userId) {
        return objectRepository.findByParentObjectIdIsNullAndUserId(userId);
    }

    public List<ObjectModel> getChildObjectsForUser(String parentId, String userId) {
        return objectRepository.findByParentObjectIdAndUserId(parentId, userId);
    }

    public List<ObjectModel> getRootObjects() {
        return objectRepository.findByParentObjectIdIsNull();
    }

    public List<ObjectModel> getChildObjects(String parentId) {
        return objectRepository.findByParentObjectId(parentId);
    }

    public ObjectModel getObjectById(String id) {
        return objectRepository.findById(id).orElse(null);
    }

    @Transactional
    public ObjectModel createObject(ObjectModel object, String userId) {
        System.out.println("=== CREATE OBJECT ===");
        System.out.println("Object name: " + object.getName());
        System.out.println("Parent ID: " + object.getParentObjectId());
        System.out.println("User ID: " + userId);

        object.setUserId(userId);
        ObjectModel saved = objectRepository.save(object);

        System.out.println("Saved object ID: " + saved.getId());
        System.out.println("Saved parent ID: " + saved.getParentObjectId());

        return saved;
    }

    @Transactional
    public ObjectModel updateObject(ObjectModel object, String userId) {
        ObjectModel existing = objectRepository.findById(object.getId()).orElse(null);
        if (existing == null || !existing.getUserId().equals(userId)) {
            throw new RuntimeException("Access denied");
        }

        object.setUserId(userId);
        ObjectModel saved = objectRepository.save(object);

        SyncMessage message = new SyncMessage(
                "UPDATE", "OBJECT", saved.getId(), saved,
                userId, new Date(), null
        );
        webSocketService.sendToUser(userId, message);

        return saved;
    }

    @Transactional
    public void deleteObject(String objectId, String userId) {
        ObjectModel object = objectRepository.findById(objectId).orElse(null);
        if (object == null || !object.getUserId().equals(userId)) {
            throw new RuntimeException("Access denied");
        }

        objectRepository.deleteById(objectId);

        SyncMessage message = new SyncMessage(
                "DELETE", "OBJECT", objectId, null,
                userId, new Date(), null
        );
        webSocketService.sendToUser(userId, message);
    }


    public Optional<User> getUserById(String userId) {
        return userRepository.findById(userId);
    }

    public Optional<User> getUserByPhoneNumber(String phoneNumber) {
        return userRepository.findByPhoneNumber(phoneNumber);
    }


    private void shareProjectWithContacts(Project project, String senderId) {
        Set<String> phoneNumbers = getContactPhones(project);

        for (String phoneNumber : phoneNumbers) {
            Optional<User> userOpt = userRepository.findByPhoneNumber(phoneNumber);
            if (userOpt.isPresent() && !userOpt.get().getId().equals(senderId)) {
                User user = userOpt.get();

                if (!sharedProjectRepository.existsByProjectIdAndSharedWithUserId(project.getId(), user.getId())) {
                    SharedProject shared = new SharedProject(project.getId(), user.getId(), senderId, "READ");
                    sharedProjectRepository.save(shared);
                }

                SyncMessage message = new SyncMessage(
                        "SHARE", "PROJECT", project.getId(), project,
                        senderId, new Date(),
                        project.getVersion() != null ? project.getVersion() : 0L
                );
                webSocketService.sendToUser(user.getId(), message);

                System.out.println("Shared project '" + project.getName() + "' with user " + user.getPhoneNumber());
            }
        }
    }

    private void notifyNewContacts(Project oldProject, Project newProject, String senderId) {
        Set<String> oldPhones = getContactPhones(oldProject);
        Set<String> newPhones = getContactPhones(newProject);
        Set<String> addedPhones = new HashSet<>(newPhones);
        addedPhones.removeAll(oldPhones);

        for (String phoneNumber : addedPhones) {
            Optional<User> userOpt = userRepository.findByPhoneNumber(phoneNumber);
            if (userOpt.isPresent() && !userOpt.get().getId().equals(senderId)) {
                User user = userOpt.get();

                if (!sharedProjectRepository.existsByProjectIdAndSharedWithUserId(newProject.getId(), user.getId())) {
                    SharedProject shared = new SharedProject(newProject.getId(), user.getId(), senderId, "READ");
                    sharedProjectRepository.save(shared);
                }

                SyncMessage message = new SyncMessage(
                        "SHARE", "PROJECT", newProject.getId(), newProject,
                        senderId, new Date(), newProject.getVersion()
                );
                webSocketService.sendToUser(user.getId(), message);

                System.out.println("Shared updated project '" + newProject.getName() + "' with new contact " + phoneNumber);
            }
        }
    }

    private Set<String> getContactPhones(Project project) {
        Set<String> phones = new HashSet<>();

        if (project.getCustomerContactId() != null) {
            addPhoneFromContact(project.getCustomerContactId(), phones);
        }
        if (project.getForemanContactId() != null) {
            addPhoneFromContact(project.getForemanContactId(), phones);
        }
        if (project.getManagerContactId() != null) {
            addPhoneFromContact(project.getManagerContactId(), phones);
        }

        return phones;
    }

    private void addPhoneFromContact(String contactId, Set<String> phones) {
        Optional<Contact> contactOpt = contactRepository.findById(contactId);
        if (contactOpt.isPresent()) {
            List<ContactMethod> methods = contactMethodRepository.findByContactId(contactId);
            for (ContactMethod method : methods) {
                String methodType = method.getMethodType().toLowerCase();
                if (methodType.contains("телефон") || methodType.contains("phone")) {
                    String phone = normalizePhoneNumber(method.getValue());
                    if (phone != null && !phone.isEmpty()) {
                        phones.add(phone);
                    }
                }
            }
        }
    }

    private String normalizePhoneNumber(String phone) {
        if (phone == null) return null;

        String digits = phone.replaceAll("[^\\d]", "");

        if (digits.isEmpty()) return null;

        if (digits.startsWith("8") && digits.length() == 11) {
            return "7" + digits.substring(1);
        }
        if (digits.startsWith("7") && digits.length() == 11) {
            return digits;
        }
        if (digits.length() == 10) {
            return "7" + digits;
        }
        if (digits.length() > 11) {
            String last11 = digits.substring(digits.length() - 11);
            if (last11.startsWith("7") || last11.startsWith("8")) {
                return normalizePhoneNumber(last11);
            }
            return "7" + last11;
        }

        return digits;
    }

    private List<String> findUsersWithAccessToProject(String projectId) {
        Set<String> userIds = new HashSet<>();

        Optional<Project> projectOpt = projectRepository.findById(projectId);
        if (projectOpt.isPresent()) {
            Project project = projectOpt.get();
            if (project.getUserId() != null) {
                userIds.add(project.getUserId());
            }
        }

        List<SharedProject> shares = sharedProjectRepository.findByProjectId(projectId);
        for (SharedProject share : shares) {
            userIds.add(share.getSharedWithUserId());
        }

        return new ArrayList<>(userIds);
    }


    public boolean hasAccessToProject(String projectId, String userId) {
        Project project = projectRepository.findById(projectId).orElse(null);
        if (project == null) return false;

        if (project.getUserId() != null && project.getUserId().equals(userId)) {
            return true;
        }

        return sharedProjectRepository.existsByProjectIdAndSharedWithUserId(projectId, userId);
    }

    private void notifyProjectParticipants(String projectId, String type, String entityType, Object data, String senderId) {
        List<String> participantIds = findUsersWithAccessToProject(projectId);

        String entityId;
        if (data instanceof String) {
            entityId = (String) data;
        } else if (data instanceof Project) {
            entityId = ((Project) data).getId();
        } else if (data instanceof Material) {
            entityId = ((Material) data).getId();
        } else if (data instanceof WorkItem) {
            entityId = ((WorkItem) data).getId();
        } else if (data instanceof Contact) {
            entityId = ((Contact) data).getId();
        } else if (data instanceof ObjectModel) {
            entityId = ((ObjectModel) data).getId();
        } else {
            entityId = null;
        }

        SyncMessage message = new SyncMessage(
                type, entityType, entityId,
                data, senderId, new Date(), null
        );

        for (String userId : participantIds) {
            if (!userId.equals(senderId)) {
                webSocketService.sendToUser(userId, message);
            }
        }
    }


    private void updateProjectTotal(String projectId) {
        List<Material> materials = materialRepository.findByProjectId(projectId);
        List<WorkItem> workItems = workItemRepository.findByProjectId(projectId);

        double totalMaterialCost = materials.stream()
                .mapToDouble(m -> m.getQuantity() * m.getUnitPrice())
                .sum();

        double totalWorkCost = workItems.stream()
                .mapToDouble(w -> w.getLaborHours() * w.getHourlyRate() + w.getMaterialCost())
                .sum();

        double totalBudget = totalMaterialCost + totalWorkCost;

        Optional<Project> projectOpt = projectRepository.findById(projectId);
        if (projectOpt.isPresent()) {
            Project project = projectOpt.get();
            project.setTotalBudget(totalBudget);
            projectRepository.save(project);
        }
    }

    public List<SharedProject> getPendingSharesForUser(String userId) {
        return sharedProjectRepository.findBySharedWithUserIdAndStatus(userId, ShareStatus.PENDING);
    }

    public List<Project> getPendingProjectsForUser(String userId) {
        List<String> pendingProjectIds = sharedProjectRepository.findSharedProjectIdsByUserAndStatus(userId, ShareStatus.PENDING);
        if (pendingProjectIds.isEmpty()) return new ArrayList<>();
        return projectRepository.findAllById(pendingProjectIds);
    }

    @Transactional
    public void acceptShare(String projectId, String userId) {
        Optional<SharedProject> shareOpt = sharedProjectRepository.findByProjectIdAndSharedWithUserId(projectId, userId);
        if (shareOpt.isPresent()) {
            SharedProject share = shareOpt.get();
            share.setStatus(ShareStatus.ACCEPTED);
            share.setRespondedAt(new Date());
            sharedProjectRepository.save(share);

            SyncMessage message = new SyncMessage(
                    "SHARE_ACCEPTED", "PROJECT", projectId, null,
                    userId, new Date(), null
            );
            webSocketService.sendToUser(share.getSharedByUserId(), message);
        }
    }

    @Transactional
    public void declineShare(String projectId, String userId) {
        Optional<SharedProject> shareOpt = sharedProjectRepository.findByProjectIdAndSharedWithUserId(projectId, userId);
        if (shareOpt.isPresent()) {
            SharedProject share = shareOpt.get();
            share.setStatus(ShareStatus.DECLINED);
            share.setRespondedAt(new Date());
            sharedProjectRepository.save(share);

            SyncMessage message = new SyncMessage(
                    "SHARE_DECLINED", "PROJECT", projectId, null,
                    userId, new Date(), null
            );
            webSocketService.sendToUser(share.getSharedByUserId(), message);
        }
    }

    @PreDestroy
    public void shutdown() {
        System.out.println("=== SYNC SERVICE SHUTDOWN ===");
        System.out.println("Cleaning up resources...");
    }

    @Transactional
    public Project saveProject(Project project, String userId) {
        Optional<Project> existing = projectRepository.findById(project.getId());

        if (existing.isPresent()) {
            Project oldProject = existing.get();
            project.setUserId(oldProject.getUserId());
            project.setOwnerId(oldProject.getOwnerId());
            project.setCreatedAt(oldProject.getCreatedAt());
            project.setCreatedBy(oldProject.getCreatedBy());
            project.setUpdatedAt(new Date());
            project.setLastModifiedBy(userId);
            project.setVersion(oldProject.getVersion());

            Project saved = projectRepository.save(project);
            notifyProjectParticipants(saved.getId(), "UPDATE", "PROJECT", saved, userId);
            return saved;
        } else {
            return createProject(project, userId);
        }
    }
}