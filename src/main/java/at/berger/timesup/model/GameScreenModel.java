package at.berger.timesup.model;

import at.berger.timesup.entity.GameEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class GameScreenModel {
    private GameEntity game;
}
