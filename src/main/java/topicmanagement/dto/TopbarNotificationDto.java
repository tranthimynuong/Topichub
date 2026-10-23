package topicmanagement.dto;

public class TopbarNotificationDto {
    private String id;
    private String title;
    private String content;
    private String category;
    private String author;
    private String createdAtFormatted;
    private String targetUrl;
    private String iconClass;
    private String iconBgClass;

    public TopbarNotificationDto() {}

    public TopbarNotificationDto(String id, String title, String content, String category, String author, String createdAtFormatted, String targetUrl, String iconClass, String iconBgClass) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.category = category;
        this.author = author;
        this.createdAtFormatted = createdAtFormatted;
        this.targetUrl = targetUrl;
        this.iconClass = iconClass;
        this.iconBgClass = iconBgClass;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getCreatedAtFormatted() {
        return createdAtFormatted;
    }

    public void setCreatedAtFormatted(String createdAtFormatted) {
        this.createdAtFormatted = createdAtFormatted;
    }

    public String getTargetUrl() {
        return targetUrl;
    }

    public void setTargetUrl(String targetUrl) {
        this.targetUrl = targetUrl;
    }

    public String getIconClass() {
        return iconClass;
    }

    public void setIconClass(String iconClass) {
        this.iconClass = iconClass;
    }

    public String getIconBgClass() {
        return iconBgClass;
    }

    public void setIconBgClass(String iconBgClass) {
        this.iconBgClass = iconBgClass;
    }
}
