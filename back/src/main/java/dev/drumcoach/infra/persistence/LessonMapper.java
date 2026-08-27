package dev.drumcoach.infra.persistence;

import dev.drumcoach.domain.Lesson;

/** Mapeamento domain.Lesson <-> infra.persistence.LessonEntity, so via construtor/factory. */
final class LessonMapper {

	private LessonMapper() {
	}

	static LessonEntity toEntity(Lesson lesson) {
		return new LessonEntity(lesson.id(), lesson.lessonDate(), lesson.teacherNotes(), lesson.feedback(),
				lesson.focusUntilNext(), lesson.suggestedMaterial(), lesson.generatedTrainingId(), lesson.createdBy(),
				lesson.lastModifiedBy(), lesson.createdAt(), lesson.updatedAt());
	}

	static Lesson toDomain(LessonEntity entity) {
		return Lesson.reconstruct(entity.id(), entity.lessonDate(), entity.teacherNotes(), entity.feedback(),
				entity.focusUntilNext(), entity.suggestedMaterial(), entity.generatedTrainingId(), entity.createdBy(),
				entity.lastModifiedBy(), entity.createdAt(), entity.updatedAt());
	}
}
