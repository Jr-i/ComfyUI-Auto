public class Remark {
    private String charFeature;
    private String locationFeature;
    private int charTotalLines;
    private int locationTotalLines;
    private int charIndex;
    private int locationIndex;

    public String getCharFeature() {
        return charFeature;
    }

    public void setCharFeature(String charFeature) {
        this.charFeature = charFeature;
    }

    public String getLocationFeature() {
        return locationFeature;
    }

    public void setLocationFeature(String locationFeature) {
        this.locationFeature = locationFeature;
    }

    public int getCharTotalLines() {
        return charTotalLines;
    }

    public void setCharTotalLines(int charTotalLines) {
        this.charTotalLines = charTotalLines;
    }

    public int getLocationTotalLines() {
        return locationTotalLines;
    }

    public void setLocationTotalLines(int locationTotalLines) {
        this.locationTotalLines = locationTotalLines;
    }

    public int getCharIndex() {
        return charIndex;
    }

    public void setCharIndex(int charIndex) {
        this.charIndex = charIndex;
    }

    public int getLocationIndex() {
        return locationIndex;
    }

    public void setLocationIndex(int locationIndex) {
        this.locationIndex = locationIndex;
    }

    public void incrementCharIndex() {
        this.charIndex++;
    }

    public void incrementLocationIndex() {
        this.locationIndex++;
    }
}