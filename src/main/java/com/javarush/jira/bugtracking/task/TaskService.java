package com.javarush.jira.bugtracking.task;

import com.javarush.jira.bugtracking.Handlers;
import com.javarush.jira.bugtracking.UserBelong;
import com.javarush.jira.bugtracking.UserBelongRepository;
import com.javarush.jira.bugtracking.sprint.Sprint;
import com.javarush.jira.bugtracking.sprint.SprintRepository;
import com.javarush.jira.bugtracking.task.mapper.TaskExtMapper;
import com.javarush.jira.bugtracking.task.mapper.TaskFullMapper;
import com.javarush.jira.bugtracking.task.to.TaskToExt;
import com.javarush.jira.bugtracking.task.to.TaskToFull;
import com.javarush.jira.common.error.DataConflictException;
import com.javarush.jira.common.error.NotFoundException;
import com.javarush.jira.common.util.Util;
import com.javarush.jira.login.AuthUser;
import com.javarush.jira.ref.RefType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.javarush.jira.bugtracking.ObjectType.TASK;
import static com.javarush.jira.bugtracking.task.TaskUtil.fillExtraFields;
import static com.javarush.jira.bugtracking.task.TaskUtil.makeActivity;
import static com.javarush.jira.ref.ReferenceService.getRefTo;

@Service
@RequiredArgsConstructor
public class TaskService {
    static final String CANNOT_ASSIGN = "Cannot assign as %s to task with status=%s";
    static final String CANNOT_UN_ASSIGN = "Cannot unassign as %s from task with status=%s";

    private final Handlers.TaskExtHandler handler;
    private final Handlers.ActivityHandler activityHandler;
    private final TaskFullMapper fullMapper;
    private final SprintRepository sprintRepository;
    private final TaskExtMapper extMapper;
    private final UserBelongRepository userBelongRepository;
    private final ActivityRepository activityRepository;


    @Transactional
    public void changeStatus(long taskId, String statusCode) {
        Assert.notNull(statusCode, "statusCode must not be null");
        Task task = handler.getRepository().getExisted(taskId);
        if (!statusCode.equals(task.getStatusCode())) {
            task.checkAndSetStatusCode(statusCode);
            Activity statusChangedActivity = new Activity(null, taskId, AuthUser.authId());
            statusChangedActivity.setStatusCode(statusCode);
            activityHandler.create(statusChangedActivity);
            String userType = getRefTo(RefType.TASK_STATUS, statusCode).getAux(1);
            if (userType != null) {
                handler.createUserBelong(taskId, TASK, AuthUser.authId(), userType);
            }
            // ----------- Добавлено: вывод времени ----------- //

            Duration dev = getDevelopmentDuration(task);
            Duration test = getTestingDuration(task);
            System.out.println("-".repeat(50));
            System.out.println("Задача id=" + taskId + " [" + task.getTitle() + "]");
            System.out.println("Время в работе: " + formatDuration(dev));
            System.out.println("Время на тестировании: " + formatDuration(test));
            System.out.println("-".repeat(50));
        }
    }

    @Transactional
    public void changeSprint(long taskId, Long sprintId) {
        Task task = handler.getRepository().getExisted(taskId);
        if (task.getParentId() != null) {
            throw new DataConflictException("Can't change subtask sprint");
        }
        if (sprintId != null) {
            Sprint sprint = sprintRepository.getExisted(sprintId);
            if (sprint.getProjectId() != task.getProjectId()) {
                throw new DataConflictException("Target sprint must belong to the same project");
            }
        }
        handler.getRepository().setTaskAndSubTasksSprint(taskId, sprintId);
    }

    @Transactional
    public Task create(TaskToExt taskTo) {
        Task created = handler.createWithBelong(taskTo, TASK, "task_author");
        activityHandler.create(makeActivity(created.id(), taskTo));
        return created;
    }

    @Transactional
    public void update(TaskToExt taskTo, long id) {
        if (!taskTo.equals(get(taskTo.id()))) {
            handler.updateFromTo(taskTo, id);
            activityHandler.create(makeActivity(id, taskTo));
        }
    }

    public TaskToFull get(long id) {
        Task task = Util.checkExist(id, handler.getRepository().findFullById(id));
        TaskToFull taskToFull = fullMapper.toTo(task);
        List<Activity> activities = activityHandler.getRepository().findAllByTaskIdOrderByUpdatedDesc(id);
        fillExtraFields(taskToFull, activities);
        taskToFull.setActivityTos(activityHandler.getMapper().toToList(activities));
        return taskToFull;
    }



    public TaskToExt getNewWithSprint(long sprintId) {
        Sprint sprint = sprintRepository.getExisted(sprintId);
        Task newTask = new Task();
        newTask.setSprintId(sprintId);
        newTask.setProjectId(sprint.getProjectId());
        return extMapper.toTo(newTask);
    }

    public TaskToExt getNewWithProject(long projectId) {
        Task newTask = new Task();
        newTask.setProjectId(projectId);
        return extMapper.toTo(newTask);
    }

    public TaskToExt getNewWithParent(long parentId) {
        Task parent = handler.getRepository().getExisted(parentId);
        Task newTask = new Task();
        newTask.setParentId(parentId);
        newTask.setSprintId(parent.getSprintId());
        newTask.setProjectId(parent.getProjectId());
        return extMapper.toTo(newTask);
    }

    public void assign(long id, String userType, long userId) {
        checkAssignmentActionPossible(id, userType, true);
        handler.createUserBelong(id, TASK, userId, userType);
    }

    @Transactional
    public void unAssign(long id, String userType, long userId) {
        checkAssignmentActionPossible(id, userType, false);
        UserBelong assignment = userBelongRepository.findActiveAssignment(id, TASK, userId, userType)
                .orElseThrow(() -> new NotFoundException(String
                        .format("Not found assignment with userType=%s for task {%d} for user {%d}", userType, id, userId)));
        assignment.setEndpoint(LocalDateTime.now());
    }

    private void checkAssignmentActionPossible(long id, String userType, boolean assign) {
        Assert.notNull(userType, "userType must not be null");
        Task task = handler.getRepository().getExisted(id);
        String possibleUserType = getRefTo(RefType.TASK_STATUS, task.getStatusCode()).getAux(1);
        if (!userType.equals(possibleUserType)) {
            throw new DataConflictException(String.format(assign ? CANNOT_ASSIGN : CANNOT_UN_ASSIGN, userType, task.getStatusCode()));
        }
    }

    // Метод получения времени сколько задача была в разработке, в тестиоовании и форматтер
    public Duration getDevelopmentDuration(Task task) {
        List<Activity> activities = activityRepository.findAllByTaskIdOrderByUpdatedDesc(task.getId());
        LocalDateTime inProgress = null, readyForReview = null;
        for (Activity a : activities) {
            if ("ready_for_review".equals(a.getStatusCode()) && readyForReview == null) {
                readyForReview = a.getUpdated();
            }
            if ("in_progress".equals(a.getStatusCode()) && inProgress == null) {
                inProgress = a.getUpdated();
            }
        }
        return (inProgress != null && readyForReview != null) ? Duration.between(inProgress, readyForReview) : null;
    }

    public Duration getTestingDuration(Task task) {
        List<Activity> activities = activityRepository.findAllByTaskIdOrderByUpdatedDesc(task.getId());
        LocalDateTime readyForReview = null, done = null;
        for (Activity a : activities) {
            if ("done".equals(a.getStatusCode()) && done == null) {
                done = a.getUpdated();
            }
            if ("ready_for_review".equals(a.getStatusCode()) && readyForReview == null) {
                readyForReview = a.getUpdated();
            }
        }
        return (readyForReview != null && done != null) ? Duration.between(readyForReview, done) : null;
    }

    public static String formatDuration(Duration duration) {
        if (duration == null) return "нет данных";
        long seconds = duration.getSeconds();
        long absSeconds = Math.abs(seconds);
        return String.format("%02d:%02d:%02d",
                absSeconds / 3600,
                (absSeconds % 3600) / 60,
                absSeconds % 60);
    }

    @Transactional
    public Set<String> appendTagsToTask(long taskId, Set<String> incomingTags) {
        Task task = handler.getRepository().findById(taskId)
                .orElseThrow(() -> new NotFoundException("Task not found"));
        incomingTags.stream()
                .filter(tag -> tag != null && !tag.trim().isEmpty())
                .forEach(tag -> task.getTags().add(tag.trim()));
        handler.getRepository().save(task);
        return new HashSet<>(task.getTags());
    }

    public Set<String> retrieveTags(long taskId) {
        return handler.getRepository().findTagsById(taskId);
    }



}
